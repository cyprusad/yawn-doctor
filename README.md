# Yawn Doctor

Explainable static analysis for risky Kotlin ORM patterns, built as a
[Detekt](https://detekt.dev) custom rule set.

## Documentation

- [Architecture](docs/architecture.md) — Why Detekt, why SARIF, design tradeoffs.
- [Rule Design](docs/rule-design.md) — Per-rule intent, matched syntax, confidence, remediation.
- [False Positives](docs/false-positives.md) — Known limitations and blind spots.
- [Demo Script](docs/demo-script.md) — Under-60-second walkthrough.
- [Learning Notes](docs/learning-notes.md) — Development log and surprises.

## What it detects

| Rule | ID | Detects |
|---|---|---|
| QueryInsideLoop | YAWN001 | ORM query terminals (`list`, `first`, `single`, `count`) inside iteration constructs (`forEach`, `map`, `for`, `while`) — the N+1 pattern. |
| MaterializedCount | YAWN002 | Query results fully materialized via `.list()` and then counted with `.size` or `.count()` instead of a database-level count. |
| ExternalCallInsideTransaction | YAWN003 | External I/O calls (matching configurable receiver/method patterns) inside a transaction scope (`@Transactional` annotation or lambda-based `transaction {}`, `open {}`, etc.). |

## Prerequisites

- JDK 17+
- Node.js 18+ and pnpm (for the dashboard / SARIF converter)

## Commands

```bash
# Run all 29 rule tests
./gradlew :doctor-rules:test

# Run the demo with coloured rustc-style output
./gradlew :demo-codebase:yawnDoctorDemo

# Generate SARIF + findings.json + verify all 3 rule IDs are present
./gradlew yawnDoctorReport

# Individual steps:
./gradlew :demo-codebase:detektMain                           # generate SARIF
cd dashboard && pnpm report && pnpm verify-report             # convert + verify

# Dashboard:
cd dashboard && pnpm dev                                      # dev server at http://localhost:3000
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
