# Yawn Doctor

Explainable static analysis for risky Kotlin ORM patterns, built as a
[Detekt](https://detekt.dev) custom rule set.

## Documentation

- [Architecture](docs/architecture.md) — Why Detekt, why SARIF, design tradeoffs.
- [Rule Design](docs/rule-design.md) — Per-rule intent, matched syntax, confidence, remediation.
- [False Positives](docs/false-positives.md) — Known limitations and blind spots.

## What it detects

| Rule | ID | Detects |
|---|---|---|
| `QueryInsideLoop` | `YAWN001` | You run a database query inside a loop (`forEach`, `map`, `for`, `while`). Each iteration fires a separate SQL query — if the loop runs 1000 times, your database gets 1000 queries instead of 1. This is the classic **N+1 problem**: one query to fetch the parent entities, then N queries for each child. The rule looks for query methods (`query`, `find`, `select`) paired with terminal calls (`list`, `single`, `count`) nested inside iteration constructs. |
| `MaterializedCount` | `YAWN002` | You call `.list()` followed by `.size` or `.count()` to find out how many rows a query returns. This forces the database to send every single row over the network just so you can count them locally. Instead, the database can count the rows itself and send back a single number — far less data, far faster. The rule flags `.list().size` and `.list().count()` chains on query results. |
| `ExternalCallInsideTransaction` | `YAWN003` | You make an external network call (HTTP request, message publish, S3 upload) while a database transaction is open. The transaction holds locks and database connections until it commits. If the external call is slow or fails, you hold those locks much longer than necessary — and the transaction might commit even though the external call failed, leaving your system in an inconsistent state. The rule detects calls to suspicious receivers (names ending in `Client`, `Publisher`, etc.) and methods (`send`, `publish`, `reserve`, etc.) inside `@Transactional` annotated functions or transactional lambda blocks. |
| `CollectionJoinWithoutDistinct` | `YAWN004` | Your ORM query joins a collection association (like `Pet.visits`) and then calls `.list()`. When a parent row has multiple children, the database returns one row per child — so the same parent appears multiple times in the result. If you don't deduplicate (with `.distinctBy { it.id }` or `.distinctRootEntity()`), you'll process the same entity repeatedly, potentially corrupting counts, pages, or downstream logic. The rule inspects lambdas passed to query constructors for `.join()` calls and checks whether the chain ends with a terminal like `.list()` without any deduplication step in between. |

## Prerequisites

- JDK 17+
- Node.js 18+ and pnpm (for the dashboard / SARIF converter)

## Commands

```bash
# Run all 29 rule tests
./gradlew :doctor-rules:test

# Run the demo with coloured rustc-style output
./gradlew :demo-codebase:yawnDoctorDemo

# Full report pipeline: SARIF → findings.json → verify → build dashboard
./gradlew yawnDoctorReport

# Individual steps:
./gradlew :demo-codebase:detektMain     # generate SARIF
./gradlew yawnDoctorConvert             # SARIF → findings.json

# Dashboard dev server at http://localhost:3000
./gradlew yawnDoctorDashboard
```

## Project structure

```
doctor-rules/          # Detekt plugin (Kotlin)
  ├── src/main/kotlin/dev/yawndoctor/
  │   ├── rules/       # QueryInsideLoop, MaterializedCount, ExternalCallInsideTransaction
  │   ├── ast/         # PSI helper functions (CallChain, LoopContext, QueryOrigin, TransactionContext)
  │   └── YawnDoctorProvider.kt
  └── src/test/kotlin/dev/yawndoctor/rules/  # 29 focused tests

demo-codebase/         # Demo Kotlin source with risky and safe examples
  └── src/main/kotlin/dev/yawndoctor/demo/
      ├── DomainTypes.kt           # Fake ORM DSL stubs
      ├── UserRepository.kt        # YAWN001: N+1 in forEach + map
      ├── BrandRepository.kt       # YAWN002: list().size
      └── FulfillmentService.kt    # YAWN003: I/O inside @Transactional + open{}

dashboard/             # Next.js dashboard + SARIF normalization (TypeScript)
  ├── app/             # Dashboard pages (Next.js App Router)
  ├── components/      # SummaryCards, SourceViewer, DetailPanel
  ├── lib/rules.ts     # Rule metadata catalog
  ├── scripts/
  │   ├── convert-sarif.ts    # SARIF → findings.json
  │   └── verify-report.ts    # asserts YAWN001-003 present
  ├── public/findings.json    # generated output (gitignored)
  └── out/             # static export (gitignored)
```

## How the detection works

The rules use Detekt's PSI (Program Structure Interface) AST traversal to
identify patterns. There is no runtime database, no bytecode analysis, and no
type resolution. Detection is deterministic — the same source always produces
the same findings.

## Performance

Analysis overhead from the 3 custom rules is negligible — roughly **9ms** added
to a cold build in benchmarks. Total detekt time is dominated by Kotlin
compilation and Detekt's own framework overhead, not the Yawn Doctor rules.

**Benchmarks (cold build = `clean` + compile + detekt on a ~15 KB project):**

| Scenario | Cold | Cached (no-op) |
|---|---|---|
| With Yawn Doctor | ~0.64s | ~0.35s |
| Without Yawn Doctor | ~0.63s | ~0.34s |
| Overhead | ~9ms | ~7ms |

Scaling to a **372 KB file with 5000 N+1 patterns**: ~8.4s cold, ~0.34s cached.

The rules use cheap operations — string name checks, short receiver-chain walks
(3–5 nodes), and parent-tree walks that stop at the nearest enclosing function.
Detekt processes files in parallel, so adding more files to a codebase scales
with available cores rather than adding sequential cost.

In practice, a single changed file triggers incremental compilation (~0.3-0.5s)
plus a full detekt re-analysis (~0.1-0.2s). The 9ms overhead of the custom
rules is the same regardless.

## Limitations

- **No interprocedural analysis.** Queries or external calls hidden behind
  helpers or variables are not detected.
- **No type resolution.** The analyzer relies on configured method/receiver
  name patterns, not runtime type information.
- **No runtime measurement.** Findings identify syntactic patterns, not actual
  round trips or result-set sizes.
- **Deterministic by design.** The same source always produces the same
  findings — no machine learning or probabilistic scores.

See [false-positives.md](docs/false-positives.md) for full details.

See `detekt.yml` for rule configuration options (query constructors, iteration
functions, transaction annotations, suspicious receiver/method patterns).
