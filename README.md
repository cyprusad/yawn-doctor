# Yawn Doctor

Explainable static analysis for risky Kotlin ORM patterns, built as a
[Detekt](https://detekt.dev) custom rule set.

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

# Dashoard:
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

See `detekt.yml` for rule configuration options (query constructors, iteration
functions, transaction annotations, suspicious receiver/method patterns).
