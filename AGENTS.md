# Agent learnings — Yawn Doctor

## Writing detekt rule tests

### Top-level code must be valid Kotlin

`for` loops, `while` loops, and bare expression statements (like
`listOf(1,2,3).forEach { … }`) are **not valid** at the top level of a regular
`.kt` file — they must live inside a `fun` body.

```kotlin
// BROKEN — for at top level is a compile error → 0 findings, no error thrown
val items = listOf(1, 2, 3)
for (i in items) { … }

// WORKS
fun test() {
    val items = listOf(1, 2, 3)
    for (i in items) { … }
}
```

`compileAndLint` silently returns an empty list when the code doesn't parse.
**Always verify test snippets compile on their own.**

### TestConfig and valueOrDefault

`TestConfig()` (no-arg) creates an empty config. Rule properties use
`valueOrDefault(key, default)` which falls back to the default when the
key is absent. This means your rule should ship with sensible defaults.

```kotlin
private val allowedNames: Set<String> by lazy {
    valueOrDefault("allowedNames", listOf("foo", "bar")).toSet()
}
```

Pass explicit values when a test needs non-default behaviour:

```kotlin
val config = TestConfig("allowedNames" to listOf("baz"))
```

### What to test

- Main positive case (pattern you're detecting)
- All variants of the pattern (different loop types, different call shapes)
- Config-driven behaviour (non-default values for rule properties)
- Negatives: no false positives from plain collection calls, unrelated methods,
  calls that match partially but not fully

---

## Commenting PSI / AST traversal code

### Draw the tree

When the logic depends on nested PSI structure, embed a tree diagram in the
comment. Labelling each `KtDotQualifiedExpression` (DQE) with an invented letter
makes it easy to reference.

```
For `session.query(UserTable).byId(id).list()`:

  DQE-A (…).list()
  ├── receiver: DQE-B (…).byId(id)
  │   ├── receiver: DQE-C session.query(UserTable)
  │   │   ├── receiver: session
  │   │   └── selector: query(UserTable)
  │   └── selector: byId(id)
  └── selector: list()
```

### Clarify traversal direction

State whether you walk **up** (toward root) or **down** (into children), and
why. `parents()` yields ancestors from nearest to farthest. `children` walks
the other way.

### Explain stopping conditions

A loop-detection walk stops at `KtNamedFunction` so that loops in outer callers
aren't falsely attributed. A receiver-chain walk stops when there is no
enclosing `KtDotQualifiedExpression`. Spell out *why* the stop matters.

### Call out the receiver / selector asymmetry

A DQE's `selectorExpression` is the part after the dot. Its
`receiverExpression` is everything before. `parent.selectorExpression == current`
matches only when `current` is the **right-hand side** — not when it's the
left-hand side (the receiver). This is the most common source of walk bugs.

---

## Detekt plugin loading pitfalls

### Gradle daemon caches stale classpath

If your custom rules don't fire after a code change:

```bash
./gradlew --stop
```

Then rebuild and retry. The daemon may hold a cached version of the plugin JAR.

### detektPlugins transitively builds the JAR

`detektPlugins(project(":my-rules"))` tells detekt to load the project's
JAR. The detekt task depends on the `jar` task via the dependency graph, so
explicit `dependsOn` is usually unnecessary.

### ServiceLoader registration

The JAR must contain:

```
META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider
```

whose content is the fully-qualified class name of your provider, e.g.
`dev.yawndoctor.YawnDoctorProvider`.

### Config merging

- `buildUponDefaultConfig = true` — start with detekt's default config, then
  overlay the provided file. Default rules (FunctionOnlyReturningConstant, …)
  are active alongside custom ones.
- `buildUponDefaultConfig = false` (default) — the provided file is the only
  source. Rules not mentioned use their own defaults (usually inactive).

Rule-set-level `active: true` and rule-level `active: true` are both required
in the YAML.

---

## Debugging rules at runtime

### Add stdout / stderr prints

A `println` or `System.err.println` in a Rule's `visit*` method or constructor
appears in the detekt output when run via Gradle.

### Check test XML reports

Test output (stdout/stderr) is captured in
`build/test-results/test/TEST-*.xml`. Read it there rather than relying on
the terminal.

### Clean build habit

After touching rule source, always:

```bash
./gradlew :my-rules:clean :my-rules:jar :target:clean :target:detekt
```

This avoids stale-JAR confusion.

---

## Project conventions

- **Test framework:** JUnit 5 via `junit-bom` + `junit-jupiter`.
- **No alpha releases:** pin to stable versions (detekt 1.23.8, Kotlin 2.0.21).
- **No commit until told:** the agent does not commit unless the user explicitly
  says so.
- **Force-push on first publish** of a branch (prior history is ephemeral).
- **Auto-generated files never committed:** `findings.json`, `node_modules/`,
  `build/` directories, and Gradle build artifacts are gitignored.

---

## Detekt typed vs untyped tasks

Detekt 1.23.x introduces typed source-set variants (`detektMain`,
`detektTest`) alongside the untyped `detekt` task. The typed variant is the
canonical one. Reports live at `build/reports/detekt/main.*` rather than
`build/reports/detekt/detekt.*`. When configuring reports, use the typed
variant explicitly:

```kotlin
tasks.register("myTask") {
    dependsOn(tasks.named("detektMain"))
}
```

Both tasks can run in the same build, so depending on
`tasks.withType<Detekt>()` triggers both — prefer the typed name.

---

## SARIF structure (detekt output)

The SARIF report at `demo-codebase/build/reports/detekt/main.sarif` has this
shape:

- `runs[0].results[].ruleId` e.g. `detekt.YawnDoctor.QueryInsideLoop`
- `runs[0].results[].message.text` — full human-readable message with `[YAWN001]`
- `runs[0].results[].locations[0].physicalLocation`:
  - `artifactLocation.uri` — `file://` absolute path
  - `region.startLine`, `startColumn`, `endLine`, `endColumn`
- `runs[0].tool.driver.rules[]` — full rule descriptors with descriptions

When writing a converter, strip `file://` from URIs, map the last segment of
`ruleId` (e.g. `QueryInsideLoop`) to YAWN IDs, and strip the `[YAWN001][HIGH]`
prefix from messages.

---

## Dashboard / TypeScript toolchain conventions

- **pnpm** is the package manager (not npm, not yarn).
- **tsx** runs TypeScript scripts directly (no compile step).
- Scripts live in `dashboard/scripts/` and are invoked via `pnpm <script>`.
- Outputs go to `dashboard/public/` (static files served by the dashboard).
- The rule metadata catalog lives in `dashboard/lib/rules.ts`, keyed by the
  short rule class name (e.g. `QueryInsideLoop`).
- `findings.json` is gitignored — always regenerate before running the
  dashboard.

### Gradle + pnpm interop

The root `build.gradle.kts` has a `yawnDoctorReport` task that chains detekt →
pnpm report → pnpm verify-report. It automatically installs pnpm dependencies
if `node_modules/` is missing.

---

## Demo code compilation requirements

Detekt parses but does not compile Kotlin. The demo codebase IS compiled when
`detektMain` runs (Gradle compiles sources before analysis). This means:
- All demo files must compile with valid Kotlin.
- Classes used as expressions (e.g. `session.query(UserTable)`) need proper
  instantiation syntax: `session.query(UserTable())`.
- Types used in inheritance must be `open`.
- Stub types with unused parameters or constant returns should be suppressed:

```kotlin
@file:Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
```
