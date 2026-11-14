# Architecture

A Spring Boot service that stores travel commerce transactions (flight, hotel, rail, Navan fee) and issues invoices that are frozen copies of them.

The service is built to **scale out** (more instances, more rows) and to **fail safely** (a crash, retry or race never leaves duplicate, torn or silently changed money). The brief rules out Redis, Kafka and lock services, so both goals rest on the same two things: a stateless app tier, and a relational database that holds all state and settles every conflict.

## 1. Shape

```
clients ──HTTP──▶  app instance 1..N       stateless: no cache, no in-JVM locks, no sessions
                   ├─ travel-app           controllers, DTOs, public ids, paging, error mapping
                   ├─ travel-invoice       service · snapshot + rollup · persistence
                   ├─ travel-transaction   service · validation · identity · persistence
                   └─ travel-common        money · paging · ids · retry
                          │  JDBC, bounded pool, lock + statement timeouts
                          ▼
                   relational database     unique index, foreign keys, row locks, transactions
```

**Modules.** The seams are Maven modules, so the build enforces the dependency direction `travel-app → travel-invoice → travel-transaction → travel-common`. The transaction module cannot reach invoice code. It asks "is this transaction billed?" through the `InvoiceUsage` interface it declares, and the invoice module implements it. Each module owns the migration for its own tables (`V1` transactions, `V2` invoices), and Flyway picks both up from the classpath.

| Seam | Code | Owns |
|---|---|---|
| HTTP | `travel-app`: `api.*Controller`, `ApiExceptionHandler` | Routing, DTOs, wire ids (`txn_…`, `inv_…`), cursors, exception → status. No SQL, no rules. |
| Transaction persistence | `travel-transaction`: `transaction.persistence` | Two public entry points: `TransactionWriter` (writes and row locks) and `TransactionLoader` (batched reads). The table-level repositories behind them are package-private: header, tax and fee lines, and one `TypeDetailsStore` per type. |
| Duplicate identity | `transaction.identity.ExternalIdentity` | `external_id` lookup and recognising a violation of `uq_transactions_external_id`. |
| Input rules | `TransactionDraftFactory` | Turns a create request or a patch into a validated draft (`MetadataReader` + `TransactionValidator`). |
| Snapshot and rollup | `travel-invoice`: `invoice.snapshot` | `InvoiceSnapshotBuilder` and `RollupCalculator`: stored transactions → frozen lines, rollups, totals. No database access. |
| Use cases | `TransactionService`, `InvoiceService` | Database transaction boundaries, lock order, the 409 rules, retries. |

**Stack:** Java 21, Spring Boot 4.1, Spring JDBC (`JdbcClient`) with hand-written SQL, Flyway. There is no ORM: the hard parts here are SQL-shaped (unique-violation handling, row locks, keyset pages, batched child loads), and every statement the service runs is visible in a repository.

**Database:** file-backed H2 by default (one command, nothing to install but a JDK). The schema and SQL stay inside what H2 and PostgreSQL share, and a `postgres` profile is included. The test suite runs on H2 only. The PostgreSQL profile was checked by hand against PostgreSQL 17: both migrations, the README curl flow, and 40 concurrent POSTs with one `external_id` (one 201, 39 409s, one row). `docker compose up` runs it that way. Nothing is kept in process memory, in main or test code.

## 2. Tables and models

```
transactions ─┬─ transaction_tax_lines        name, rate?, amount
              ├─ transaction_fee_lines        name, amount
              ├─ flight_details ── flight_coupons
              ├─ hotel_details  ── hotel_extras
              ├─ rail_legs
              └─ navan_fee_details

invoices ─┬─ invoice_lines ─┬─ invoice_line_tax_lines
          │                 └─ invoice_line_fee_lines
          ├─ invoice_tax_rollups
          └─ invoice_fee_rollups
```

| Table | Key columns | Notes |
|---|---|---|
| `transactions` | `id` identity PK, `external_id` (**unique index**), `type`, `currency`, `occurred_at`, `total`, `version`, timestamps | `CHECK type IN (…)`. `type`, `external_id`, `currency` cannot be patched. |
| `transaction_tax_lines` | PK `(transaction_id, line_no)`, `name`, `rate` nullable, `amount` | Government/VAT-style charges. |
| `transaction_fee_lines` | PK `(transaction_id, line_no)`, `name`, `amount` | Carrier/merchant fees. Its own table: a fee is never a tax line with a flag. |
| `flight_details` | PK `transaction_id`, `origin`, `destination`, `fare` | |
| `flight_coupons` | PK `(transaction_id, coupon_no)`, `carrier`?, `flight_number`, `from_location`, `to_location`, `departure_at`, `arrival_at` | One row per segment. |
| `hotel_details` | PK `transaction_id`, `name`, `address_street`, `address_city`, `address_country`, `check_in`, `check_out`, `nights`, `room_rate_per_night` | Address is three real columns. |
| `hotel_extras` | PK `(transaction_id, extra_no)`, `name`, `amount` | Optional (breakfast). |
| `rail_legs` | PK `(transaction_id, leg_no)`, `origin`, `destination`, `departure_at`, `arrival_at`, `train_number`?, `travel_class`? | A change of train is two rows. |
| `navan_fee_details` | PK `transaction_id`, `fee_kind`, `description`?, `related_external_id`? | `CHECK fee_kind IN ('trip_fee', 'agent_call_fee')`. `related_external_id` is a soft reference: a fee may arrive before its booking. |

Child rows reference `transactions(id) ON DELETE CASCADE`, so deleting a transaction and all its lines is one statement. The composite primary key on each child table is also the index used to read it in order.

In Java the same structure is a `Transaction` record holding `List<TaxLine>`, `List<FeeLine>` and a sealed `TransactionMetadata` with four records (`FlightMetadata`, `HotelMetadata`, `RailMetadata`, `NavanFeeMetadata`). `MetadataReader` picks the record from `type`, so a flight body cannot be stored as a hotel; `TransactionValidator` rejects `{}` and any missing required field. Unknown `type` or `fee_kind` is a 400. Unknown extra metadata keys are ignored, not stored.

**Why child tables per type.** Typed columns get `NOT NULL` and `CHECK` constraints and can be indexed the day a query needs them (`flight_coupons.flight_number`); the schema documents the model. Single-table inheritance would still need child tables for coupons and legs. The cost is more statements per write, which is bounded (caps on list sizes) and, on reads, batched (§6). JSON is used where it fits: the frozen metadata copy on an invoice line (§4), which is immutable and always read whole.

**Money.** `NUMERIC(19,4)` in the database and `BigDecimal` in Java; JSON numbers are parsed straight to `BigDecimal`. An amount may carry at most the currency's ISO 4217 minor units (EUR 2, JPY 0); more precise input is a 400, never rounded. Responses render amounts at the currency's scale (`220.00`). Tax rates are fractions in `NUMERIC(9,6)` (`0.07`). `total` is the client's gross figure; tax and fee lines break it down and are not added on top. Timestamps are stored as `TIMESTAMP WITH TIME ZONE` and returned in UTC.

## 3. `external_id` uniqueness and concurrent POSTs

`external_id` identifies a transaction across all types, compared exactly. The guarantee is the unique index `uq_transactions_external_id`; application code only decides how to answer.

1. **Identity first.** Look up `external_id` (one index probe). If it exists, answer 409 before validating the body. This is why the brief's `duplicate_flight` fixture (with `coupons: []`) gets 409, not 400.
2. Validate the body (400 on failure).
3. In one database transaction insert the header, then tax lines, fee lines and type details. The header goes first, so a losing request fails before writing any child row.
4. If the insert violates the unique index, the transaction rolls back, the winner's id is read, and the answer is 409. If the winner is not visible yet, the attempt is retried (§8) and ends in 409 once it is.

Concurrent POSTs all pass step 1, and the index lets exactly one commit. This holds across any number of instances because nothing depends on JVM state. `DuplicateExternalIdTest` fires 16 simultaneous POSTs per round for five rounds; with debug logging on, most of the 75 losers are caught by the index at step 4, not by the pre-check.

**Contract:** duplicate → `409 Conflict`, body `{"error":"duplicate_external_id","transaction_id":"<existing id>",…}` (a transaction's server id is always named `transaction_id`, in the 201 body too) and a `Location` header. The stored row is never modified by a duplicate. A client that times out can safely retry a create.

## 4. Invoice snapshot

Creating an invoice copies; it never links.

| Stored | Content |
|---|---|
| `invoices` | `currency`, `total`, `line_count`, `version`, timestamps |
| `invoice_lines` | One per transaction: `line_no`, `transaction_id` and the copy: `external_id`, `type`, `category`, `currency`, `occurred_at`, `total`, `transaction_version`, `metadata` (the typed metadata serialised to JSON text, returned verbatim). `CHECK` constraints on `type` and `category` (V3). |
| `invoice_line_tax_lines`, `invoice_line_fee_lines` | Copies of the transaction's lines, still decimal columns |
| `invoice_tax_rollups`, `invoice_fee_rollups` | Rollups, computed once at write time |

`GET /invoices/{id}` reads only `invoice*` tables; no read path joins `transactions`. The per-category `totals` are summed from the frozen lines at read time, so they are frozen too. `InvoiceSnapshotTest` edits the stored flight's total, tax line and coupon with raw SQL and shows the invoice unchanged (flight 220.00, grand total 685.70).

**Rules.**

- Tax rollup groups by `(name, rate)`; fee rollup by `name`. Names are trimmed, rates compare numerically (`0.07` = `0.070000`), a missing rate is its own group, order is first appearance.
- `totals` holds one entry per category plus `grand_total`. The category is the type, except `navan_fee` splits by `fee_kind`, matching `expected-invoice.json`. Each frozen line keeps its `TransactionType` and, for `navan_fee`, its `FeeKind`; the category is the fee kind when there is one, otherwise the type. `totals` are listed by type, then fee kind, in enum order (`flight`, `hotel`, `rail`, `trip_fee`, `agent_call_fee`). `transaction_types_included` and `navan_fee_kinds_included` are also derived from the frozen lines at read time, deduplicated and in enum order. `grand_total` is the sum of transaction totals.
- Mixed currencies: 409 `mixed_currency`. The same id twice in one request: 409 `duplicate_transaction_id` (also enforced by `UNIQUE (invoice_id, transaction_id)`). Any missing id: 404 naming it. At most 100 ids.
- A transaction may be on several invoices (the fixture shows single-id and trip invoices over the same flight).

**Three layers keep an invoice frozen:** PATCH or DELETE of an invoiced transaction returns 409; `invoice_lines.transaction_id` references `transactions` with `ON DELETE RESTRICT`; and the snapshot is a physical copy.

`PATCH /invoices/{id}` deletes the old snapshot and writes a new one from the current transactions in one database transaction; on 404 or 409 the old snapshot is untouched. `DELETE /invoices/{id}` cascades to its own lines and rollups only. Once no invoice holds a transaction, it becomes editable again.

## 5. Concurrency: who locks what

Isolation is `READ COMMITTED` plus explicit row locks (`SELECT … FOR UPDATE`). Any writer whose outcome depends on a transaction's state locks that transaction row first.

| Operation | Locks, in order | Checked under the lock |
|---|---|---|
| `POST /transactions` | none; the unique index decides | |
| `PATCH` / `DELETE /transactions/{id}` | transaction row | exists; not on any invoice |
| `POST /invoices` | transaction rows, ascending id | all exist; one currency |
| `PATCH /invoices/{id}` | invoice row, then transaction rows ascending | same |
| `DELETE /invoices/{id}` | invoice row (via `DELETE`) | |

- Two PATCHes on one transaction serialize on its row lock. Child lists are replaced (delete, insert) only while holding it, so the stored list is always exactly one request's list. `TransactionUpdateDeleteTest` races eight PATCHes with different `tax_lines` and checks for that.
- A PATCH racing an invoice over the same transaction: either the invoice commits first and the PATCH then sees the invoice line and returns 409, or the PATCH commits first and the invoice snapshots the new values. Never a mix.
- Deadlocks are avoided by the fixed order: invoice row before transaction rows, transaction rows by ascending id. Transaction paths never lock invoices.

## 6. High volume

| Pressure | What would break | What is in place |
|---|---|---|
| Large lists | `OFFSET` scans grow with depth; unbounded selects exhaust memory | Keyset pagination on the primary key (`WHERE id < :cursor ORDER BY id DESC LIMIT :limit+1`). `limit` defaults to 50 and is capped at 100 (the response echoes the effective limit); `limit < 1` is 400. The opaque `next_cursor` is absent on the last page. No `COUNT(*)`, no offsets, no in-memory paging. |
| Hydrating a page | One query per row per child table | A fixed number of `IN (…)` queries per page, whatever its size: headers, tax lines, fee lines, then one or two per type present. |
| Duplicate POST storms | Wasted writes and lock pile-ups | A duplicate costs one index probe. Racing duplicates fail on their first statement, before any child row. |
| Concurrent PATCH | Torn lists, lost money | Parent row lock, short transactions, no table locks. |
| "Is it invoiced?" on every PATCH/DELETE | Scan of `invoice_lines` | `ix_invoice_lines_transaction_id`, probed with `FETCH FIRST 1 ROWS ONLY`. |
| Oversized requests | One request holding locks for long | Caps: 100 ids per invoice, 50 tax/fee/extra lines, 20 coupons or legs. |

**Indexes** (in `V1__create_transaction_tables.sql` and `V2__create_invoice_tables.sql`, asserted by `SchemaTest`):

| Index | Serves |
|---|---|
| `transactions` PK | `GET /transactions/{id}`, list paging, row locks |
| `uq_transactions_external_id` | Duplicate identity |
| Child PKs `(transaction_id, line_no / coupon_no / leg_no / extra_no)` | Ordered, batched child loads |
| `invoices` PK | `GET /invoices/{id}`, list paging |
| `uq_invoice_lines_transaction (invoice_id, transaction_id)` | Lines of one invoice; no double count |
| `ix_invoice_lines_transaction_id` | "Is this transaction on an invoice?" |

**What fails first.** With the above, the write path on the single database. Lock waits on a hot row each hold a pooled connection; once the pool (20) is empty, new requests would queue. Lock timeout (3 s), statement timeout (10 s) and pool-acquire timeout (3 s) turn that into fast 503s instead of a stall, and those 503s are safe to retry (§8). `DatabaseContentionTest` holds a row lock from a second connection and shows a PATCH returning 503 with `Retry-After` within seconds, then succeeding once the lock is released. Beyond that the ceiling is the database's raw write throughput.

## 7. Scaling out

- **App tier:** stateless; add instances behind any load balancer. Correctness does not depend on instance count or routing.
- **Bounded cost per request:** every query is index-driven and every list, page and payload is capped, so latency stays flat as tables grow.
- **Opaque ids** (`txn_17`) and cursors: switching to UUIDv7 or a shard-friendly scheme would not change the client contract.

Next steps, not built: run on PostgreSQL with a standby; read replicas for GETs and lists; a connection pooler once instances × pool size nears the database's limit; time-range partitioning of `transactions` and `invoice_lines`; a tenant column with `UNIQUE (tenant_id, external_id)` as the shard key.

## 8. Fault tolerance

| Failure | Behaviour |
|---|---|
| App crashes mid-request | The open database transaction rolls back; each write is one transaction, so a record is entirely present or absent. `AtomicCreateTest` forces a failure after the header and lines are written and finds no rows. |
| Client times out and retries a create | Idempotent on `external_id`: 201 once, then 409 with the same id. |
| Client retries PATCH or DELETE | PATCH replaces whole fields, so repeating it converges. A repeated DELETE returns 404. |
| Lock timeout, deadlock, racing insert not yet visible | `TransientFailureRetry` re-runs the whole database transaction up to 4 times with jittered backoff; safe because the failed attempt rolled back. Then 503. |
| Database unreachable or slow | Pool, lock and statement timeouts; the API answers 503 with `Retry-After: 1` instead of hanging. `/health/readiness` includes the database, `/health/liveness` does not, so a load balancer drains the instance without restarting it. |
| Instance stopped or redeployed | No local state to lose; graceful shutdown lets in-flight requests finish (20 s). |
| Several instances start together | Flyway locks its history table, so migrations run once. |

There is one store and no cache, queue or second database, so there are no dual-write failure modes.

**Known gap.** `POST /invoices` is not idempotent: a retried request creates a second invoice. The fix is an `Idempotency-Key` header stored in a nullable unique column on `invoices`, the same technique as `external_id`.

## 9. Decisions that could have gone the other way

| Decision | Alternative | Why this one |
|---|---|---|
| Duplicate `external_id` returns 409 | 200 with the existing resource | The client can tell "created" from "already existed", and a body that differs from the stored one is not reported as success. |
| Same id twice on an invoice returns 409 | Dedupe silently | A repeated id is a client bug worth surfacing. |
| `limit` above 100 is clamped | 400 | Clients still page correctly; the response states the effective limit. |
| Cursor paging only | Also accept `offset` | Offsets get slower with depth and skip or repeat rows under concurrent inserts. |
| Hard delete | Soft delete | The brief asks for deletion; a deleted `external_id` can be reused. |
| Flight needs ≥ 1 coupon, rail ≥ 1 leg, hotel `nights` = days between dates | Accept anything structurally valid | A ticket without a segment is not a ticket. |
| `total` not cross-checked against its parts | Enforce fare + taxes + fees = total | Inclusive and exclusive pricing differ by type; the client's gross is authoritative. |
