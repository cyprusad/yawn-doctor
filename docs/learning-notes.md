# Learning Notes — Yawn Doctor

## Phase 0 — Repository inspection and decisions

### Environment
- JDK: Zulu 17.0.17 (matches plan)
- Gradle: 9.6.1 installed via brew; wrapper pinned to 8.12.1 (detekt 1.23.8 compat)
- Detekt: 1.23.8 (latest stable; 2.x is alpha-only)
- Kotlin: 2.0.21 (pinned to match detekt 1.23.8's compiled version)

### Deviation from plan
- Plan pinned Kotlin 2.0.21, Detekt 1.23.8 — we kept detekt 1.23.8 but updated Gradle
  from the plan's unstated version to 8.12.1 (as recommended by detekt 1.23.8's own build).
- `buildUponDefaultConfig` is a Gradle DSL property, not a detekt YAML config key.

### What is Detekt?
A static-analysis framework for Kotlin. It parses Kotlin source into PSI trees and
visits them with rule implementations. It supports custom rule sets via Java ServiceLoader.

### What does a custom Detekt rule receive?
A parsed `KtFile` (PSI tree) and a `Config` object. The rule extends `Rule` (which extends
`DetektVisitor` → `KtTreeVisitorVoid`) and overrides visit methods. It reports findings
via the `report()` method inherited from `BaseRule`.

### What is PSI?
Program Structure Interface — JetBrains' tree representation of source code. Each node
(`KtCallExpression`, `KtDotQualifiedExpression`, etc.) represents a syntactic element.
PSI trees preserve source offsets and parent-child relationships.

### Why is KSP not the primary tool for expression analysis?
KSP generates code from annotated symbols at compile time. It does not provide a
traversable AST of the source file. Detekt (via PSI) lets us walk expressions, lambdas,
and control flow — which is what pattern-based static analysis requires.
