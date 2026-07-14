# False Positives and Known Limitations

## General

- **No interprocedural analysis.** Queries or external calls hidden behind
  helper methods are not detected. A query stored in a variable and later used
  inside a loop is also missed.
- **No runtime measurement.** The tool identifies syntactic patterns, not actual
  database round trips, lock duration, or result-set sizes. A finding means "this
  pattern often causes problems", not "this is definitely a performance bug."
- **No type resolution.** The analyzer does not know whether a receiver with a
  matching name is actually an ORM query or an external client. It relies on
  configured method and receiver names.
- **Deterministic by design.** The same source always produces the same
  findings. There are no machine-learning models, probabilistic scores, or
  runtime data involved.

## YAWN001 — QueryInsideLoop

- **False negatives** occur when the query is constructed outside the loop and
  only the terminal is called inside the loop (e.g. `val q = session.query(...)`
  then `q.list()` in a `forEach`). The constructor is not in the receiver chain
  of the terminal call.
- **False positives** are unlikely for the matched pattern, but a query inside a
  loop can occasionally be intentional — for example, a batch-processing job
  that deliberately processes one record at a time with a cursor.
- The rule does not distinguish between a loop that iterates over 3 elements
  and one that iterates over 3 million.

## YAWN002 — MaterializedCount

- **False negatives** occur when entities are materialized and counted through a
  variable: `val results = query(...).list(); results.size`. The rule checks the
  receiver chain, not variables.
- **False positives** are unlikely for `.list().size` on a query chain, but the
  rule does not actually know the result-set size. A query that always returns
  ≤10 rows is flagged the same as one that returns 100,000 rows.
- The rule does not detect equivalent patterns like `.list().isNotEmpty()` which
  also forces hydration.

## YAWN003 — ExternalCallInsideTransaction

- **False positives** can occur when a local method happens to match a
  suspicious name pattern (e.g. a helper named `publish` that only writes to an
  in-memory list). The receiver regex and method-name filters are the main
  defence, and they are configurable.
- **False negatives** occur when an external client is named unconventionally
  (e.g. `orderService.sendInvoice` would be caught, but `myHttp.post` might not
  match `.*Client`). Users should extend the configuration to match their
  codebase.
- Transaction scope is detected through annotations and lambda-passing patterns
  only. A transaction started programmatically (e.g. `transactionManager.begin()`
  in a try-finally block) is not detected.
- The rule does not know whether a method performs actual network I/O. A
  `ShippingClient.reserve()` that simply writes to a local cache is flagged the
  same as one that calls a REST API.

## YAWN004 — CollectionJoinWithoutDistinct

- **False negatives** occur when deduplication is applied through a mechanism
  the rule does not recognise (e.g. a custom `distinct` extension function, or
  deduplication via a `HashSet` after `.list()`). Only methods listed in
  `distinctMethods` are considered as deduplication.
- **False positives** are possible when a query joins a collection but the
  application guarantees one-to-one cardinality (e.g. via a unique constraint),
  making deduplication redundant. The rule cannot reason about database schema
  or constraints.
- The rule only inspects lambdas passed to query constructors. A join applied
  to the query object after construction (e.g. `query.adjustColumnSet { ... }`)
  is not detected.
- The `joinFunctionNames` and `queryConstructors` lists are configurable in
  `detekt.yml` to match the actual method names used in the codebase.
- **SQL `DISTINCT` is not a solution.** `SELECT DISTINCT` only eliminates
  fully-duplicate rows. When child columns differ (as they do in a one-to-many
  join), every row is unique and `DISTINCT` returns all of them. Root-entity
  deduplication requires application-level logic or a native ORM API
  (e.g. JPA's `DISTINCT_ROOT_ENTITY`).
- **Application-level deduplication is a workaround.** `.distinctBy { it.id }`
  collapses the returned list but does not prevent the database from
  transferring and hydrating the duplicate rows. The join overhead (network
  transfer, result-set materialization, object construction) has already
  occurred before `.distinctBy` runs.
