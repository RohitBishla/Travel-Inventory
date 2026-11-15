# Travel transactions and invoices

A Spring Boot service that stores flight, hotel, rail and Navan fee transactions and issues invoices as frozen snapshots of them. Data lives in MariaDB, split across two shards. Design and trade-offs are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Requirements

- **JDK 21.** Maven is not needed; the wrapper downloads it. On macOS: `brew install openjdk@21`.
- **MariaDB 10.6 or newer** on `localhost:3306`, with a `travel` user allowed to create `travel_*` databases. On macOS:

  ```bash
  brew install mariadb && brew services start mariadb
  mariadb < db/create-user.sql        # creates user travel / travel
  ```

  The app creates its two shard databases, `travel_shard_0` and `travel_shard_1`, and their tables on first start.

## Run the API

```bash
./run.sh
```

This builds the app and starts it on `http://localhost:8080`. `./run.sh` finds a JDK 21 by itself; `./run.sh help` lists its other commands: `build`, `test`, `reset` (drops both shard databases) and `rebalance` (see [Adding a shard](#adding-a-shard)). Without the script: `./mvnw spring-boot:run`.

`SHARD_0_URL`, `SHARD_1_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD` point the app at other databases. `GET /health` reports status; `/health/readiness` (checks both shards) and `/health/liveness` are the probe endpoints. Swagger UI is at `http://localhost:8080/swagger-ui.html` (`SWAGGER_ENABLED=false` turns it off).

## Run the tests

```bash
./mvnw test
```

The tests use the same MariaDB server (`TEST_DATABASE_HOST` overrides `localhost`) and recreate their own databases, `travel_test_*`, on every run. The HTTP tests start the app on a random port and call it like a client. The brief's required tests:

| # | Requirement | Test |
|---|---|---|
| 1 | Create every type from the seeds; GET returns coupons, address, legs, `fee_kind` | `TransactionCreateTest.createsEveryTypeFromSeedsAndReadsBackTheTypedStructure` |
| 2 | Duplicate `external_id` returns 409 with the existing id (`transaction_id`); one row | `DuplicateExternalIdTest.secondPostWithSameExternalIdReturnsConflictWithExistingId` |
| 3 | Trip invoice matches `expected-invoice.json` (685.70 EUR) | `InvoiceCreateTest.tripInvoiceMatchesExpectedInvoiceFixture` |
| 4 | Invoice unchanged after the stored flight total changes | `InvoiceSnapshotTest.invoiceKeepsBilledValuesAfterTheStoredFlightChanges` |
| 5 | EUR + USD invoice returns 409 | `InvoiceCreateTest.mixedCurrenciesAreRejected` |
| 6 | Overlapping POSTs with one `external_id` leave one row | `DuplicateExternalIdTest.overlappingPostsWithSameExternalIdLeaveExactlyOneRow` |
| 7 | PATCH of an invoiced transaction returns 409; snapshot unchanged | `InvoiceSnapshotTest.patchingAnInvoicedTransactionIsRejectedAndChangesNothing` |
| 8 | DELETE of an invoiced transaction returns 409; row remains | `TransactionUpdateDeleteTest.deletingAnInvoicedTransactionIsRejectedAndKeepsTheRow` |
| 9 | DELETE invoice keeps its transactions | `InvoiceUpdateDeleteTest.deletingAnInvoiceKeepsItsTransactions` |
| 10 | Default page size 50; `limit=1000` capped at 100 | `PaginationTest.listDefaultsToFiftyAndCapsLargeLimitsAtOneHundred` |

Beyond those, `ShardingTest`, `HashRingTest` and `AddShardTest` cover the sharding (below). `./mvnw test` runs 80 tests in total.

## API

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/transactions` | 201 + `Location`; body is the stored transaction, server id in `transaction_id` | 400 invalid, 409 duplicate `external_id` (body carries the existing `transaction_id`) |
| `GET` | `/transactions/{id}` | 200 | 404 |
| `GET` | `/transactions?limit=&cursor=` | 200 | 400 bad `limit` or cursor |
| `PATCH` | `/transactions/{id}` | 200 | 400, 404, 409 if on an invoice |
| `DELETE` | `/transactions/{id}` | 204 | 404, 409 if on an invoice |
| `POST` | `/invoices` | 201 + `Location` | 400, 404 missing id, 409 mixed currency or repeated id |
| `GET` | `/invoices/{id}` | 200 | 404 |
| `GET` | `/invoices?limit=&cursor=` | 200 | 400 |
| `PATCH` | `/invoices/{id}` | 200 | 400, 404, 409 (invoice left unchanged) |
| `DELETE` | `/invoices/{id}` | 204 | 404 |

Errors share one shape: `{"error": "<code>", "message": "...", "details": [...]}`. Any database contention that outlasts the retries returns 503 with `Retry-After`.

### Conventions

- **Ids** are `txn_<n>` and `inv_<n>`: unique across shards, but not consecutive (see [Sharding](#sharding)).
- **A transaction's id is the `transaction_id` field**; there is no separate `id` on a transaction. Pass it to `GET /transactions/{id}` and `POST /invoices`.
- **An invoice's id is `id`.** It also lists `transaction_ids`, `transaction_types_included` and `navan_fee_kinds_included`, the last two in a fixed order as in `expected-invoice.json`.
- **Money** is a JSON number with at most the currency's minor units (EUR: 2 decimals). Amounts are stored as `DECIMAL(19,4)` and never rounded.
- **Lists** return newest first by creation time, merged across both shards: `{"items": [...], "limit": 50, "next_cursor": "..."}`. `limit` defaults to 50 and is capped at 100. The first page needs no cursor; pass `next_cursor` back as `cursor` for the next one. It is absent on the last page.
- **PATCH /transactions** accepts `occurred_at`, `total`, `tax_lines`, `fee_lines`, `metadata`; each field sent replaces the stored one. `type`, `external_id` and `currency` cannot be changed.
- **Invoice bodies** are `{"transaction_id": "..."}` or `{"transaction_ids": [...]}`, for both POST and PATCH.

## Example curls

These run in order in one shell against fresh shards (`./run.sh reset` before starting the API). Each create stores the id the server returns in a variable; the comments show the ids a fresh database assigns. They use `jq` to pick payloads out of the fixture file.

```bash
API=http://localhost:8080
SEEDS=fixtures/task-b/seed-payloads.json
post() { curl -s -X POST "$API$1" -H 'Content-Type: application/json' -d "$2"; echo; }
create() { curl -s -X POST "$API/transactions" -H 'Content-Type: application/json' -d "$1" | jq -r .transaction_id; }
```

### Create one transaction of each type

```bash
FLIGHT=$(create "$(jq .flight $SEEDS)")             # txn_1    (shard 0)
HOTEL=$(create "$(jq .hotel $SEEDS)")               # txn_65   (shard 0)
RAIL=$(create "$(jq .rail $SEEDS)")                 # txn_129  (shard 0)
TRIP_FEE=$(create "$(jq .trip_fee $SEEDS)")         # txn_2    (shard 1)
AGENT_FEE=$(create "$(jq .agent_call_fee $SEEDS)")  # txn_193  (shard 0)
curl -s $API/transactions/$FLIGHT | jq -c '{transaction_id, type, external_id, total}'
# {"transaction_id":"txn_1","type":"flight","external_id":"flight-TXL-LHR-2026-03-11","total":220.00}
```

### Duplicate create: POST the same `external_id` twice, one row exists

```bash
post /transactions "$(jq .duplicate_flight $SEEDS)"
# 409 {"error":"duplicate_external_id","message":"...","transaction_id":"txn_1"}

post /transactions "$(jq .duplicate_flight $SEEDS)"
# 409 again, same id

curl -s "$API/transactions?limit=100" \
  | jq '[.items[] | select(.external_id == "flight-TXL-LHR-2026-03-11")] | length'
# 1
```

### Get and list transactions

```bash
curl -s $API/transactions/$FLIGHT | jq .metadata.coupons
curl -s "$API/transactions?limit=2" | jq '{ids: [.items[].transaction_id], next_cursor}'
# newest first across both shards: ["txn_193", "txn_2"]
curl -s "$API/transactions?limit=2&cursor=<next_cursor from the previous call>"
```

### Patch and delete a transaction (before it is invoiced)

```bash
curl -s -X PATCH $API/transactions/$RAIL -H 'Content-Type: application/json' \
  -d '{"total": 96.00, "fee_lines": [{"name": "Booking fee", "amount": 6.00}]}'

TEMP=$(create '{"type":"navan_fee","external_id":"navan-trip-fee-temp","currency":"EUR",
  "occurred_at":"2026-03-12T09:00:00Z","total":10.00,"tax_lines":[],"fee_lines":[],
  "metadata":{"fee_kind":"trip_fee"}}')
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $API/transactions/$TEMP   # 204

curl -s -X PATCH $API/transactions/$RAIL -H 'Content-Type: application/json' \
  -d '{"total": 94.00, "fee_lines": [{"name": "Booking fee", "amount": 4.00}]}'   # restore the seed values
```

### Create an invoice by id

```bash
post /invoices "{\"transaction_id\": \"$FLIGHT\"}"                                    # inv_1
TRIP="[\"$FLIGHT\",\"$HOTEL\",\"$RAIL\",\"$TRIP_FEE\",\"$AGENT_FEE\"]"
INVOICE=$(curl -s -X POST $API/invoices -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": $TRIP}" | jq -r .id)                                # inv_65, spans both shards
curl -s $API/invoices/$INVOICE | jq -c '{id, total, totals}'
# {"id":"inv_65","total":685.70,"totals":{"flight":220.00,"hotel":331.70,"rail":94.00,"trip_fee":25.00,"agent_call_fee":15.00,"grand_total":685.70}}
```

### Patch and delete an invoiced transaction: 409

```bash
curl -s -X PATCH $API/transactions/$FLIGHT -H 'Content-Type: application/json' -d '{"total": 999.00}'
# 409 {"error":"transaction_invoiced",...}

curl -s -X DELETE $API/transactions/$TRIP_FEE
# 409 {"error":"transaction_invoiced",...}   (the trip fee is on shard 1, the invoice on shard 0)
```

### Get and list invoices

```bash
curl -s $API/invoices/$INVOICE | jq '{transaction_ids, transaction_types_included, navan_fee_kinds_included, total, totals, tax_rollup, fee_rollup}'
curl -s "$API/invoices?limit=10" | jq -c '.items[] | {id, total, line_count}'
```

### Patch and delete an invoice

```bash
curl -s -X PATCH $API/invoices/inv_1 -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": [\"$HOTEL\", \"$RAIL\"]}" | jq -c '{total, totals, version}'
# snapshot rebuilt from current data: total 425.70, version 2

curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $API/invoices/inv_1   # 204
curl -s $API/transactions/$HOTEL | jq .transaction_id                    # still there
```

### Validation and mixed currency

```bash
post /transactions '{"type":"car","external_id":"car-1"}'               # 400 unknown type
post /transactions "$(jq '.trip_fee | .external_id = "fee-x" | .metadata.fee_kind = "lounge"' $SEEDS)"   # 400 unknown fee_kind
USD_FEE=$(create "$(jq '.trip_fee | .external_id = "fee-usd" | .currency = "USD"' $SEEDS)")
post /invoices "{\"transaction_ids\": [\"$TRIP_FEE\", \"$USD_FEE\"]}"          # 409 mixed_currency
```

## Sharding

Every table exists on both shards. Where a row goes:

- **Transactions** are placed by `external_id` on a consistent-hash ring (SHA-256, 256 points per shard). All requests for one `external_id` reach the same shard, so its unique index rejects duplicates, even from concurrent POSTs.
- **Ids** tell you the shard: shard `s` issues `s+1, s+65, s+129, …`, so `txn_2` is on shard 1 and `txn_65` on shard 0. No lookup table is needed.
- **Invoices** live on the lowest shard among their transactions. The trip invoice in the curls above spans both shards and still freezes correctly: each transaction's shard keeps a link to the invoice, written before the invoice commits and removed after it is deleted. [ARCHITECTURE.md](ARCHITECTURE.md) section 2 explains why that is safe without a distributed commit.
- **Lists** ask every shard for its newest rows and merge them by creation time.

### Adding a shard

A new shard takes over about 1/N of the keys, all from the existing shards, and no transaction moves. Only the small `external_ids` records that guard uniqueness move. Shard ids (0-63) are permanent; never renumber or remove a shard that holds data.

1. Add the shard with the next id, and set `previous-ring` to the shards that were there before:

   ```yaml
   travel:
     sharding:
       shards:
         - { id: 0, url: "jdbc:mariadb://localhost:3306/travel_shard_0?createDatabaseIfNotExist=true" }
         - { id: 1, url: "jdbc:mariadb://localhost:3306/travel_shard_1?createDatabaseIfNotExist=true" }
         - { id: 2, url: "jdbc:mariadb://localhost:3306/travel_shard_2?createDatabaseIfNotExist=true" }
       previous-ring: [0, 1]
   ```

2. Restart **all** instances together (stop all, then start). While `previous-ring` is set, a create also checks the key's old shard, so existing `external_id`s stay unique. A rolling restart here could let two instances accept the same new key.
3. Run `./run.sh rebalance` to move the uniqueness records to their new shards. Running it again moves nothing.
4. Remove `previous-ring` and restart; a rolling restart is fine now.

`AddShardTest` runs these four steps end to end against three databases.

## Docker

```bash
docker compose up --build
```

Starts MariaDB 11.4 (with the `travel` user from `db/create-user.sql`) and the app on `http://localhost:8080`, both shards on that one server. `APP_PORT=8090 docker compose up --build` publishes it on another port; `docker compose down -v` stops everything and deletes the data. On macOS without Docker Desktop: `brew install colima docker docker-compose docker-buildx && colima start`.

## Code quality

`./mvnw verify` also writes a coverage report for all modules to `travel-app/target/site/jacoco-aggregate/index.html`. To run SonarQube locally:

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:community   # then create a token at http://localhost:9000
./mvnw verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token> -Dsonar.projectKey=travel-invoice
```

Last run: quality gate passed with 0 bugs, 0 vulnerabilities, 0 code smells, 0 security hotspots, A ratings, 85% coverage and no duplication.

## Project layout

A Maven multi-module build. Dependencies point one way, `app → invoice → transaction → common`, and the build enforces it: the transaction module cannot see invoice code.

| Module | Contents | Migration |
|---|---|---|
| `travel-common` | Money rules, paging, public ids, retry, shared exceptions; `shard/`: the hash ring, routing, shard-scoped database access | |
| `travel-transaction` | Transaction model and sealed metadata types, validation, `external_id` uniqueness and rebalancing, persistence | `V1__create_transaction_tables.sql` |
| `travel-invoice` | Invoice service (the cross-shard write order), snapshot builder and rollups, persistence, billing links | `V2__create_invoice_tables.sql` |
| `travel-app` | HTTP controllers, DTOs, error mapping, Spring Boot entry point, shard pools, migrations and the `rebalance` command (`sharding/`), HTTP tests | |

```
travel-transaction/src/main/java/com/navan/travel/transaction/
  TransactionService        use cases, lock rules, duplicate handling
  TransactionDraftFactory   request or patch -> validated draft (MetadataReader + TransactionValidator)
  identity/                 external_ids lookups, duplicate detection, IdentityRebalancer
  metadata/                 FlightMetadata, HotelMetadata, RailMetadata, NavanFeeMetadata
  persistence/              TransactionWriter and TransactionLoader (public);
                            header, line and per-type detail repositories (package-private)
travel-invoice/src/main/java/com/navan/travel/invoice/
  InvoiceService            use cases, cross-shard lock and commit order
  BilledTransactionCheck    "is this transaction billed?" across shards
  snapshot/                 InvoiceSnapshotBuilder, RollupCalculator (no database access)
  persistence/              invoice header, frozen invoice lines, invoice_links
```

Unit tests sit next to the code they cover (`MoneyTest`, `PageQueryTest`, `HashRingTest`, `TransactionValidatorTest`, `RollupCalculatorTest`); the HTTP tests live in `travel-app`.
