# Travel transactions and invoices

A Spring Boot service that stores flight, hotel, rail and Navan fee transactions and issues invoices as frozen snapshots of them. Design and trade-offs are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Requirements

JDK 21. Maven is not needed; the wrapper downloads it. On macOS: `brew install openjdk@21` and `export JAVA_HOME=$(brew --prefix openjdk@21)`.

## Run the API

```bash
./run.sh            # builds without tests, then starts the API
```

`./run.sh` finds a JDK 21 by itself (`JAVA_HOME`, `java_home` or Homebrew). It also has `build`, `test` and `reset` (wipes `./data`); see `./run.sh help`. Without the script: `./mvnw spring-boot:run`.

Run it from the repository root. The API listens on `http://localhost:8080` and stores data in an H2 file database under `./data/`. Flyway creates the schema on start. To run the packaged jar instead: `./mvnw package -DskipTests && java -jar travel-app/target/travel-app-0.0.1-SNAPSHOT.jar`. `GET /health` reports status; `/health/readiness` and `/health/liveness` are the probe endpoints.

To use PostgreSQL instead, set `DATABASE_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD` and run with `--spring.profiles.active=postgres`. The automated tests run on H2; the curl flow below has also been run by hand against PostgreSQL 17.

### Swagger

With the API running, open `http://localhost:8080/swagger-ui.html` to try every endpoint from the browser. The OpenAPI 3.1 spec is at `/v3/api-docs`. Set `SWAGGER_ENABLED=false` to turn both off.

### Docker

```bash
docker compose up --build
```

This builds the app image and starts it on `http://localhost:8080` with PostgreSQL 17 (data in the `postgres-data` volume). `docker compose down -v` stops everything and deletes the data. To run the image alone on the H2 file database: `docker build -t travel-invoice . && docker run -p 8080:8080 travel-invoice`.

## Run the tests

```bash
./mvnw test
```

Each module's unit tests run first. Then the `travel-app` tests start the app on a random port and call it over HTTP, against a fresh file database under `travel-app/target/test-db/`. The brief's required tests map to:

| # | Requirement | Test |
|---|---|---|
| 1 | Create every type from the seeds; GET returns coupons, address, legs, `fee_kind` | `TransactionCreateTest.createsEveryTypeFromSeedsAndReadsBackTheTypedStructure` |
| 2 | Duplicate `external_id` returns 409 with the existing id; one row | `DuplicateExternalIdTest.secondPostWithSameExternalIdReturnsConflictWithExistingId` |
| 3 | Trip invoice matches `expected-invoice.json` (685.70 EUR) | `InvoiceCreateTest.tripInvoiceMatchesExpectedInvoiceFixture` |
| 4 | Invoice unchanged after the stored flight total changes | `InvoiceSnapshotTest.invoiceKeepsBilledValuesAfterTheStoredFlightChanges` |
| 5 | EUR + USD invoice returns 409 | `InvoiceCreateTest.mixedCurrenciesAreRejected` |
| 6 | Overlapping POSTs with one `external_id` leave one row | `DuplicateExternalIdTest.overlappingPostsWithSameExternalIdLeaveExactlyOneRow` |
| 7 | PATCH of an invoiced transaction returns 409; snapshot unchanged | `InvoiceSnapshotTest.patchingAnInvoicedTransactionIsRejectedAndChangesNothing` |
| 8 | DELETE of an invoiced transaction returns 409; row remains | `TransactionUpdateDeleteTest.deletingAnInvoicedTransactionIsRejectedAndKeepsTheRow` |
| 9 | DELETE invoice keeps its transactions | `InvoiceUpdateDeleteTest.deletingAnInvoiceKeepsItsTransactions` |
| 10 | Default page size 50; `limit=1000` capped at 100 | `PaginationTest.listDefaultsToFiftyAndCapsLargeLimitsAtOneHundred` |

## API

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/transactions` | 201 + `Location` | 400 invalid, 409 duplicate `external_id` (body carries the existing `transaction_id`) |
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

Conventions:

- **Ids** are `txn_<n>` and `inv_<n>`. A transaction carries its id as `transaction_id`; an invoice as `id`, with its transactions in `transaction_ids`.
- **Money** is a JSON number with at most the currency's minor units (EUR: 2 decimals). Amounts are stored as `NUMERIC(19,4)` and never rounded.
- **Lists** return newest first: `{"items": [...], "limit": 50, "next_cursor": "..."}`. `limit` defaults to 50 and is capped at 100. Pass `next_cursor` back as `cursor`; it is absent on the last page.
- **PATCH /transactions** accepts `occurred_at`, `total`, `tax_lines`, `fee_lines`, `metadata`; each field sent replaces the stored one. `type`, `external_id` and `currency` cannot be changed.
- **Invoice bodies** are `{"transaction_id": "..."}` or `{"transaction_ids": [...]}`, for both POST and PATCH.

## Example curls

These run in order against a fresh database (`rm -rf data` before starting the API), so the ids below are the ones the server will assign. They use `jq` to pick payloads out of the fixture file.

```bash
API=http://localhost:8080
SEEDS=fixtures/task-b/seed-payloads.json
post() { curl -s -X POST "$API$1" -H 'Content-Type: application/json' -d "$2"; echo; }
```

### Create one transaction of each type

```bash
post /transactions "$(jq .flight $SEEDS)"           # txn_1
post /transactions "$(jq .hotel $SEEDS)"            # txn_2
post /transactions "$(jq .rail $SEEDS)"             # txn_3
post /transactions "$(jq .trip_fee $SEEDS)"         # txn_4
post /transactions "$(jq .agent_call_fee $SEEDS)"   # txn_5
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
curl -s $API/transactions/txn_1 | jq .metadata.coupons
curl -s "$API/transactions?limit=2" | jq '{ids: [.items[].transaction_id], next_cursor}'
curl -s "$API/transactions?limit=2&cursor=<next_cursor from the previous call>"
```

### Patch and delete a transaction (before it is invoiced)

```bash
curl -s -X PATCH $API/transactions/txn_3 -H 'Content-Type: application/json' \
  -d '{"total": 96.00, "fee_lines": [{"name": "Booking fee", "amount": 6.00}]}'

post /transactions '{"type":"navan_fee","external_id":"navan-trip-fee-temp","currency":"EUR",
  "occurred_at":"2026-03-12T09:00:00Z","total":10.00,"tax_lines":[],"fee_lines":[],
  "metadata":{"fee_kind":"trip_fee"}}'                                    # txn_6
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $API/transactions/txn_6   # 204

curl -s -X PATCH $API/transactions/txn_3 -H 'Content-Type: application/json' \
  -d '{"total": 94.00, "fee_lines": [{"name": "Booking fee", "amount": 4.00}]}'   # restore the seed values
```

### Create an invoice by id

```bash
post /invoices '{"transaction_id": "txn_1"}'                                              # inv_1
post /invoices '{"transaction_ids": ["txn_1","txn_2","txn_3","txn_4","txn_5"]}'           # inv_2
# total 685.70; totals {"flight":220.00,"hotel":331.70,"rail":94.00,"trip_fee":25.00,"agent_call_fee":15.00,"grand_total":685.70}
```

### Patch and delete an invoiced transaction: 409

```bash
curl -s -X PATCH $API/transactions/txn_1 -H 'Content-Type: application/json' -d '{"total": 999.00}'
# 409 {"error":"transaction_invoiced",...}

curl -s -X DELETE $API/transactions/txn_1
# 409 {"error":"transaction_invoiced",...}
```

### Get and list invoices

```bash
curl -s $API/invoices/inv_2 | jq '{transaction_ids, total, totals, tax_rollup, fee_rollup}'
curl -s "$API/invoices?limit=10" | jq '.items[] | {id, total, line_count}'
```

### Patch and delete an invoice

```bash
curl -s -X PATCH $API/invoices/inv_1 -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_2", "txn_3"]}' | jq '{total, totals, version}'
# snapshot rebuilt from current data: total 425.70, version 2

curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $API/invoices/inv_1   # 204
curl -s $API/transactions/txn_2 | jq .transaction_id                      # still there
```

### Validation and mixed currency

```bash
post /transactions '{"type":"car","external_id":"car-1"}'               # 400 unknown type
post /transactions "$(jq '.trip_fee | .external_id = "fee-x" | .metadata.fee_kind = "lounge"' $SEEDS)"   # 400 unknown fee_kind
post /transactions "$(jq '.trip_fee | .external_id = "fee-usd" | .currency = "USD"' $SEEDS)"           # txn_7
post /invoices '{"transaction_ids": ["txn_4", "txn_7"]}'                # 409 mixed_currency
```

## Project layout

A Maven multi-module build. Dependencies point one way, `app → invoice → transaction → common`, and the build enforces it: the transaction module cannot see invoice code.

| Module | Contents | Migration |
|---|---|---|
| `travel-common` | Money rules, paging, public ids, retry, shared exceptions | |
| `travel-transaction` | Transaction model and sealed metadata types, validation, `external_id` identity, persistence | `V1__create_transaction_tables.sql` |
| `travel-invoice` | Invoice service, snapshot builder and rollups, persistence | `V2__create_invoice_tables.sql` |
| `travel-app` | HTTP controllers, DTOs, error mapping, Spring Boot entry point, configuration, API tests | |

```
travel-transaction/src/main/java/com/navan/travel/transaction/
  TransactionService        use cases, lock rules, duplicate handling
  TransactionDraftFactory   request or patch -> validated draft (MetadataReader + TransactionValidator)
  identity/                 external_id lookup and unique-violation detection
  metadata/                 FlightMetadata, HotelMetadata, RailMetadata, NavanFeeMetadata
  persistence/              TransactionWriter and TransactionLoader (public);
                            header, line and per-type detail repositories (package-private)
travel-invoice/src/main/java/com/navan/travel/invoice/
  InvoiceService            use cases, lock order
  snapshot/                 InvoiceSnapshotBuilder, RollupCalculator (no database access)
  persistence/              invoice header, snapshot lines and rollups
```

Unit tests sit next to the code they cover (`MoneyTest`, `PageQueryTest`, `TransactionValidatorTest`, `RollupCalculatorTest`). The HTTP tests live in `travel-app`. `./mvnw test` runs all of them, 65 in total.
