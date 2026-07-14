# Rule Design

---

## YAWN001 — QueryInsideLoop

### Intent

Detect obvious N+1-style query patterns where a recognized ORM query executes
inside a loop or collection-iteration lambda.

### Matched syntax

- `for` loops
- `while` loops
- Lambdas passed to: `forEach`, `map`, `flatMap`, `associate`, `onEach`
- Query terminals: `list`, `first`, `single`, `count`
- Query constructors: `query`, `createYawnCriteria`, `createCriteria`

### Deliberately unmatched syntax

- Queries hidden behind helper methods or local variables.
- Stream or reactive-pipeline equivalents (the MVP targets imperative Kotlin).
- Runtime query counts — the rule only checks syntax.

### Confidence

**High.** The matched pattern (query terminal inside iteration) is almost always
a sign of an N+1 problem, provided the query constructor is visible in the same
receiver chain.

### Violation example

```kotlin
userIds.forEach { id ->
    val user = session.query(UserTable).byId(id).list()
}
```

### Safe direction

```kotlin
val users = session.query(UserTable).byId(userIds).list()
```

### Remediation options

- Collect identifiers before the loop and fetch matching rows in one bulk query.
- Use a batch-loading strategy such as Hibernate's IN clause.
- Restructure the code so the query is not inside the iteration.

### Test cases

10 tests covering: `list()` inside `forEach`, `for`, `map`, `while`;
configurable terminal/constructor names; negative cases for plain collections
and unrelated `.list()` calls.

### Known limitations

- Queries hidden behind helpers are not detected.
- No runtime query count is measured.
- A query inside a loop can occasionally be intentional (e.g. batch processing
  with a cursor).
- Syntax-only analysis cannot prove database behavior.

---

## YAWN002 — MaterializedCount

### Intent

Detect ORM query results that are fully materialized and then counted in memory.

### Matched syntax

- `<query-chain>.list().size`
- `<query-chain>.list().count()`

### Deliberately unmatched syntax

- `ordinaryList.size` or `ordinaryList.count()` — no query constructor in chain.
- Dedicated count projections like `.countProjection()`.
- Hidden materialization through variables or helper methods.

### Confidence

**High.** The pattern `.list().size` on a query-receiver chain is overwhelmingly
likely to be an unnecessary hydration.

### Violation example

```kotlin
return session.query(OrderTable).byId(brandId).list().size
```

### Safe direction

```kotlin
return session.query(OrderTable).byId(brandId).countProjection()
```

### Remediation options

- Use a database-level count projection (e.g. `SELECT COUNT(*)`).
- Use a dedicated count query that returns a scalar `Long`.
- Prefer `countProjection()` when the ORM API provides it.

### Test cases

9 tests covering: `.list().size`, `.list().count()`, multiline chains,
ordinary collections, dedicated projections.

### Known limitations

- The analyzer cannot know whether the result set is always tiny.
- May miss materialization hidden behind a helper or variable.
- Cannot estimate actual transfer or hydration cost.

---

## YAWN003 — ExternalCallInsideTransaction

### Intent

Detect likely external I/O while a database transaction is active.

### Matched syntax

- Calls inside a function annotated with `@Transactional` (configurable).
- Calls inside a lambda passed to `transaction`, `inTransaction`, `withTransaction`,
  or `open` (configurable).
- Calls that match configurable receiver regex patterns (e.g. `.*Client`, `.*Publisher`)
  or configurable method names (e.g. `send`, `publish`, `reserve`).

### Deliberately unmatched syntax

- Calls outside any transaction scope.
- Recognized ORM query calls (to avoid double-reporting with YAWN001).
- Local methods that happen to have a suspicious name.
- Calls in separately compiled libraries.

### Confidence

**Medium.** Naming-based heuristics can produce false positives for local
methods with suspicious names, and false negatives for external clients with
unusual naming.

### Violation example

```kotlin
@Transactional
fun fulfillOrder(orderId: Long) {
    shippingClient.reserve(orderId)
}
```

### Safe direction

```kotlin
fun fulfillOrder(orderId: Long) {
    shippingClient.reserve(orderId)  // no transaction active
}
```

### Remediation options

- Move external I/O outside the transaction when safe.
- Commit the database transaction before calling the external system.
- Use a transactional outbox pattern.
- Ensure external operations are idempotent.

### Test cases

10 tests covering: annotated functions, lambda-based transactions, receiver
regex matching, method name matching, configuration overrides, negative cases
outside transactions and for local helpers.

### Known limitations

- Naming-based detection can miss external clients with unusual names.
- Local methods can have suspicious names.
- Static analysis cannot know whether a method performs network I/O.
- Interprocedural transaction propagation is not handled.
- The best remediation depends on consistency requirements.

---

## YAWN004 — CollectionJoinWithoutDistinct

### Intent

Detect ORM queries that join a collection association (one-to-many, many-to-many)
without deduplicating the result, causing duplicate parent rows in the returned
list.

### Matched syntax

- Lambda passed to configurable query constructors (`query`, `select`, `from`)
  that contains a `.join()` call.
- Terminal calls: `list`, `toList`, `asList`, `single`, `first`, `one`, `find`.
- Deduplication indicators that suppress the finding: `distinctRootEntity`, `distinctBy`, `Distinct`.

### Deliberately unmatched syntax

- `.join()` inside a lambda that does *not* pass through a recognized query
  constructor (false positives on plain collection joins).
- Fetch joins that do not duplicate rows (e.g. single-ended associations like
  many-to-one). The rule cannot distinguish association types without type
  resolution, so it flags all joins. Users silence by adding a deduplication step.

### Confidence

**Medium.** A join inside a query constructor is a strong signal, but the rule
cannot determine whether the joined association is collection-valued without
type resolution. If a join is on a single-ended (many-to-one) association, no
duplication occurs and the finding is a false positive.

### Violation example

```kotlin
val authors = session.query(AuthorTable) {
    join(BookTable::class)
}.list()
// if Author → Book is one-to-many, each author appears once per book
```

### Safe direction

```kotlin
val authors = session.query(AuthorTable) {
    join(BookTable::class)
}.distinctRootEntity().list()
```

### Remediation options

- Add `.distinctRootEntity()` (or `.distinctBy { it.id }`) to the query chain.
- Use a batch-query / secondary query pattern instead of a join.
- If the association is single-ended, suppress the finding manually.

### Test cases

11 tests covering: `.join()` in lambda, various terminals, multiple joins,
deduplication indicators (`distinctRootEntity`, `distinctBy`, `Distinct`),
configurable constructor names, negative cases for joins outside queries.

### Known limitations

- No type resolution means the rule cannot distinguish collection joins from
  single-ended joins — all joins are flagged.
- Joins constructed through string-based APIs or helper functions are not detected.
- The deduplication step may itself have overhead for large result sets.
