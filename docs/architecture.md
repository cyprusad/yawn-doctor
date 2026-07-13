# Architecture

## Why Detekt

Detekt parses Kotlin source into a PSI (Program Structure Interface) AST and
visits it with rule implementations. This allows pattern-based static analysis
without requiring a runtime database, Hibernate setup, or bytecode
instrumentation. Custom rule sets are loaded via Java's `ServiceLoader` —
no plugin registry or build-system hook is needed beyond adding a dependency.

## Why not KSP

Kotlin Symbol Processing (KSP) generates code from annotated symbols at compile
time. It does not provide a traversable AST of the source file — you cannot walk
call expressions, dot-qualified chains, or control-flow constructs the way
Detekt's PSI visitors can. KSP is designed for annotation-processing use cases
(like Room or Moshi), not for pattern-based static analysis.

## Why not an IntelliJ plugin for the MVP

An IntelliJ plugin would require a different distribution mechanism, a
different testing harness, and would only run inside the IDE. By building on
Detekt, Yawn Doctor runs from the command line and in CI, produces standard
SARIF output, and integrates with GitHub code scanning — all without an IDE
dependency.

## Why SARIF separates analysis from presentation

The analyzer (Detekt plugin) produces a standard SARIF file. A separate
conversion step normalizes the SARIF into a frontend-friendly JSON schema,
enriching findings with rule metadata (explanations, impact, remediations).
The dashboard is a pure static site that consumes this JSON. This means:

- The analyzer never serves HTTP or depends on the frontend.
- The dashboard never parses Kotlin or decides whether a finding exists.
- Either layer can be replaced independently.
- The same SARIF can be uploaded to GitHub code scanning without the dashboard.

## Why deterministic detection is separated from AI remediation

Findings are always produced by deterministic PSI rules — no AI ever creates or
suppresses a finding. If an AI remediation feature is added later, it receives
a single finding and limited context, produces a reviewable patch, and clearly
labels all output as generated. Detection is the source of truth; AI is an
optional explanation and suggestion layer.

## Why the demo codebase uses a fake DSL

The demo codebase compiles and runs, but it uses stub types (e.g. `Query<T>`,
`Session`, `ShippingClient`) that provide the method signatures needed for
analysis without depending on Hibernate, a real database, or network services.
This keeps the demo self-contained, fast to build, and deterministic.
