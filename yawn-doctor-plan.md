# Yawn Doctor — Build Plan and Working Specification

## 1. Project summary

**Yawn Doctor** is an explainable static-analysis tool for Kotlin backend code.

It will be implemented as a custom Detekt plugin that detects a small number of high-value ORM and transaction patterns that may create performance, reliability, or service-boundary problems.

The first version detects:

1. ORM query execution inside loops.
2. ORM entity materialization followed by an in-memory count.
3. External I/O inside a database transaction.

The analyzer emits normal Detekt findings and SARIF. A small dashboard consumes the report and presents findings with source annotations, explanations, confidence levels, and remediation guidance.

An optional AI feature may generate a reviewable refactoring proposal, but AI must never decide whether a violation exists.

### Product thesis

Database problems often enter a codebase through ordinary-looking application code. Yawn Doctor converts a few high-value backend engineering practices into repeatable, explainable checks that can run during local development and CI.

### Core design principle

> Detection is deterministic. AI may explain or propose a patch, but deterministic rules remain the source of truth.

---

## 2. What success looks like

A good first demo should let a viewer understand the project in under 60 seconds.

The viewer should be able to:

1. Open the dashboard.
2. See three findings in a small Kotlin demo codebase.
3. Select a finding.
4. See the exact source line that triggered it.
5. Understand:
   - what syntax was detected;
   - why it may be risky;
   - the confidence level;
   - possible remediation directions.
6. Run one command to regenerate the analysis.
7. Optionally view the same findings as GitHub code-scanning annotations.

The first version does **not** need a database, running Hibernate application, Docker environment, authentication, repository upload workflow, or IDE plugin.

---

## 3. Scope

### Required MVP features

- Kotlin multi-module Gradle project.
- Custom Detekt rule set.
- Three deterministic rules.
- Focused unit tests for every rule.
- Demo Kotlin source containing both risky and safe examples.
- Detekt SARIF output.
- SARIF-to-dashboard JSON normalization.
- Single-page dashboard with source annotations.
- Documentation for architecture, limitations, and rule behavior.
- One command or short command sequence that regenerates the demo report.

### Optional features

Implement only after the required MVP is complete:

- AI-generated refactoring proposal.
- GitHub Actions workflow that uploads SARIF.
- Stable SARIF fingerprints.
- Baseline support.
- A fourth rule for collection joins that may duplicate root entities.
- Type-resolution support.
- IntelliJ integration.

### Explicit non-goals

Do not build these during the initial implementation:

- A real database.
- Spring Boot.
- Runtime Hibernate setup.
- Full Yawn integration.
- KSP processors.
- Whole-program data-flow analysis.
- Query-plan analysis.
- Bytecode analysis.
- Automatic semantic rewrites.
- Repository cloning through a web UI.
- Authentication.
- Multi-user support.
- Arbitrary ORM support.
- A generic “AI scans the whole codebase” feature.
- A hosted SaaS backend.

---

## 4. Suggested technology baseline

Use this as the initial baseline unless a concrete compatibility issue requires a change:

- JDK 17
- Kotlin 2.0.21
- Gradle Kotlin DSL
- Detekt 1.23.8
- JUnit 5
- TypeScript
- Next.js for the dashboard
- SARIF as the interchange format

These versions are intentionally pinned for reproducibility. Do not upgrade dependencies casually during the first implementation.

---

## 5. Suggested repository structure

```text
yawn-doctor/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── detekt.yml
├── README.md
├── PLAN.md
├── doctor-rules/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/dev/yawndoctor/
│       │   │   ├── YawnDoctorProvider.kt
│       │   │   ├── RuleCatalog.kt
│       │   │   ├── model/
│       │   │   │   └── DoctorRuleDescriptor.kt
│       │   │   ├── ast/
│       │   │   │   ├── CallChain.kt
│       │   │   │   ├── LoopContext.kt
│       │   │   │   ├── QueryOrigin.kt
│       │   │   │   └── TransactionContext.kt
│       │   │   └── rules/
│       │   │       ├── QueryInsideLoopRule.kt
│       │   │       ├── MaterializedCountRule.kt
│       │   │       └── ExternalCallInsideTransactionRule.kt
│       │   └── resources/
│       │       └── META-INF/services/
│       │           └── io.gitlab.arturbosch.detekt.api.RuleSetProvider
│       └── test/
│           └── kotlin/dev/yawndoctor/rules/
├── demo-codebase/
│   ├── build.gradle.kts
│   └── src/main/kotlin/demo/
│       ├── DemoDsl.kt
│       ├── BadOrderRepository.kt
│       ├── BadBrandService.kt
│       ├── GoodOrderRepository.kt
│       └── GoodBrandService.kt
├── dashboard/
│   ├── app/
│   ├── components/
│   ├── lib/
│   ├── public/
│   ├── scripts/
│   │   └── convert-sarif.ts
│   └── package.json
├── docs/
│   ├── architecture.md
│   ├── rule-design.md
│   ├── false-positives.md
│   ├── ai-remediation.md
│   ├── learning-notes.md
│   └── demo-script.md
└── .github/
    └── workflows/
        └── yawn-doctor.yml
```

The exact package names may change, but preserve the separation between:

- analysis code;
- demo fixtures;
- report normalization;
- presentation;
- documentation.

---

## 6. Architectural boundaries

### Kotlin analyzer

Responsibilities:

- Parse Kotlin source through Detekt.
- Detect deterministic rule violations.
- Preserve source locations.
- Emit concise messages.
- Produce SARIF.

It must not:

- call an LLM;
- serve HTTP;
- depend on the dashboard;
- need a database;
- invent runtime measurements.

### SARIF normalization layer

Responsibilities:

- Read Detekt SARIF.
- Convert it into a small frontend-friendly schema.
- Merge rule metadata with SARIF findings.
- Copy or embed demo source content for display.
- Fail clearly when expected findings are missing.

It must not:

- reimplement the rules;
- guess source locations;
- classify findings differently from the analyzer.

### Dashboard

Responsibilities:

- Load normalized report data.
- Display findings, source, explanations, and remediation guidance.
- Clearly label deterministic findings.
- Optionally request an AI-generated proposal.

It must not:

- decide whether code violates a rule;
- require a live analyzer process;
- require AI to function.

### Optional AI remediation layer

Responsibilities:

- Receive one finding and the containing function.
- Return a suggested patch, assumptions, and validation steps.
- Mark all output as generated and review-required.

It must not:

- create or suppress findings;
- automatically apply code;
- claim semantic correctness;
- receive an entire private repository for the demo.

---

## 7. Shared rule metadata

Create a shared metadata model in the Kotlin analyzer:

```kotlin
data class DoctorRuleDescriptor(
    val id: String,
    val title: String,
    val category: String,
    val severity: String,
    val confidence: String,
    val summary: String,
    val impact: String,
    val remediations: List<String>,
    val documentationSlug: String,
)
```

Suggested catalog:

### YAWN001 — Query inside loop

- Category: `DATABASE_ROUND_TRIPS`
- Severity: warning
- Confidence: high
- Summary: A recognized ORM query terminal executes inside an iteration construct.
- Impact: Database round trips may grow linearly with input size.
- Remediation directions:
  - Collect identifiers before the loop.
  - Fetch matching rows in one bulk query.
  - Group results in memory by foreign key.

### YAWN002 — Materialized count

- Category: `UNNECESSARY_HYDRATION`
- Severity: warning
- Confidence: high
- Summary: ORM entities are materialized before computing a scalar count.
- Impact: The application may transfer and hydrate many entities unnecessarily.
- Remediation directions:
  - Use a database-level count projection.
  - Use a dedicated count query.
  - Avoid loading full entities when only a scalar is required.

### YAWN003 — External call inside transaction

- Category: `TRANSACTION_BOUNDARIES`
- Severity: warning
- Confidence: medium or high depending on match quality
- Summary: A configured external I/O call appears inside a database transaction.
- Impact: Locks may be held longer and partial-failure behavior may become ambiguous.
- Remediation directions:
  - Move external I/O outside the transaction when safe.
  - Commit state before calling the external system.
  - Use a transactional outbox.
  - Make retried operations idempotent.

The frontend may duplicate this catalog in TypeScript for the MVP. A later improvement can generate frontend metadata from one machine-readable source.

---

## 8. Rule configuration

Use `detekt.yml`.

```yaml
YawnDoctor:
  QueryInsideLoop:
    active: true
    queryConstructors:
      - query
      - createYawnCriteria
      - createCriteria
    queryTerminals:
      - list
      - first
      - single
      - count
    iterationFunctions:
      - forEach
      - map
      - flatMap
      - associate
      - onEach

  MaterializedCount:
    active: true
    queryConstructors:
      - query
      - createYawnCriteria
      - createCriteria

  ExternalCallInsideTransaction:
    active: true
    transactionAnnotations:
      - Transactional
    transactionFunctions:
      - transaction
      - inTransaction
      - withTransaction
    suspiciousReceiverPatterns:
      - ".*Client"
      - ".*Publisher"
      - ".*Producer"
      - "httpClient"
    suspiciousMethodNames:
      - send
      - publish
      - post
      - execute
      - reserve
      - notify
      - sleep
```

Keep configuration parsing simple. Do not create a large abstraction framework before the rules work.

---

## 9. Rule specification: QueryInsideLoop

### Intent

Detect obvious N+1-style query patterns where a recognized ORM query executes inside a loop or collection-iteration lambda.

### Example violation

```kotlin
fun loadItems(orders: List<Order>): Map<Long, List<OrderItem>> {
    return orders.associate { order ->
        val items = session.createYawnCriteria(OrderItemTable) { item ->
            addEq(item.orderId, order.id)
        }.list()

        order.id to items
    }
}
```

### Safe direction

```kotlin
fun loadItems(orders: List<Order>): Map<Long, List<OrderItem>> {
    val orderIds = orders.map { it.id }

    return session.createYawnCriteria(OrderItemTable) { item ->
        addIn(item.orderId, orderIds)
    }.list().groupBy { it.orderId }
}
```

The analyzer does not need to generate this exact code.

### Required matched contexts

- `for` loop
- `while` loop
- lambda passed to:
  - `forEach`
  - `map`
  - `flatMap`
  - `associate`
  - `onEach`

### Required query shape

For the first version, only report when a configured query constructor and configured query terminal are visible in the same expression or receiver chain.

Do not implement variable tracking or interprocedural analysis in the MVP.

### Suggested implementation approach

Visit `KtCallExpression`.

For each call:

1. Extract the callee name.
2. Return unless it is a configured terminal.
3. Inspect its receiver chain.
4. Confirm a configured query-constructor call exists.
5. Walk ancestors.
6. Detect loop syntax or iteration lambdas.
7. Report the terminal call.

### Finding location

Report on the terminal call such as `list()`, not on the containing function or file.

### Message

```text
[YAWN001][HIGH] Query terminal `list()` executes inside `forEach`.
Database round trips may grow with the number of input elements.
```

### Required tests

- Reports `list()` inside `forEach`.
- Reports `list()` inside `for`.
- Reports a recognized query inside `map`.
- Reports a recognized query inside `while`.
- Does not report a query executed before the loop.
- Does not report `listOf()` inside a loop.
- Does not report normal collection calls.
- Does not report an unrelated `.list()` with no recognized query origin.
- Reports the correct source location.
- Respects configured terminal names.

### Known limitations

- Queries hidden behind helper methods are not detected.
- Queries stored in variables may not be detected.
- No runtime query count is measured.
- A query inside a loop can occasionally be intentional.
- Syntax-only analysis cannot prove database behavior.

---

## 10. Rule specification: MaterializedCount

### Intent

Detect ORM query results that are fully materialized and then counted in memory.

### Example violations

```kotlin
val count = session.createYawnCriteria(OrderTable) { order ->
    addEq(order.brandId, brandId)
}.list().size
```

```kotlin
val count = session.createYawnCriteria(OrderTable) { order ->
    addEq(order.brandId, brandId)
}.list().count()
```

### Recommendation

The message must recommend a database-level count projection or dedicated count query.

Do not invent an exact Yawn API unless the demo DSL explicitly provides one.

### Required matched shapes

- `<recognized-query>.list().size`
- `<recognized-query>.list().count()`

### Suggested implementation approach

Visit `KtDotQualifiedExpression`.

Use a hybrid method:

1. Identify the relevant dot-qualified expression using PSI.
2. Match the final small chain shape.
3. Separately inspect the receiver chain for a configured query constructor.
4. Report only when both checks pass.

It is acceptable for the final small shape check to inspect `expression.text`, but do not scan entire files with regex.

### Finding location

Report on `size` or `count`.

### Message

```text
[YAWN002][HIGH] Query results are materialized before counting.
Use a database-level count projection or dedicated count query.
```

### Required tests

- Reports `.list().size`.
- Reports `.list().count()`.
- Reports a multiline call chain.
- Does not report `ordinaryList.size`.
- Does not report `ordinaryList.count()`.
- Does not report `results.size` when the query origin is no longer visible.
- Does not report a dedicated count projection.
- Reports the correct source location.
- Respects configured query constructors.

### Known limitations

- The analyzer cannot know whether the result set is always tiny.
- It may miss materialization hidden behind a helper or variable.
- It cannot estimate actual transfer or hydration cost.
- It recommends a direction rather than a guaranteed rewrite.

---

## 11. Rule specification: ExternalCallInsideTransaction

### Intent

Detect likely external I/O while a database transaction is active.

### Example violation

```kotlin
@Transactional
fun fulfillOrder(orderId: Long) {
    val order = loadOrder(orderId)
    order.markFulfilled()

    shippingClient.reserveShipment(order.id)
}
```

Another supported shape:

```kotlin
fun fulfillOrder(orderId: Long) {
    transaction {
        updateOrder(orderId)
        eventPublisher.publish(OrderFulfilled(orderId))
    }
}
```

### Transaction contexts

Recognize:

1. A containing function annotated with a configured transaction annotation.
2. A containing lambda passed to a configured transaction function.

### Suspicious calls

Recognize through configurable receiver-name regexes and method names.

A call should be reported only when transaction context exists and the call matches suspicious receiver or method configuration.

### False-positive reduction

Do not report:

- recognized ORM query calls;
- repository calls merely because they use a method named `find`;
- ordinary local collection operations;
- calls outside transaction scope.

Be conservative.

### Suggested implementation approach

For every `KtCallExpression`:

1. Extract method name.
2. Extract receiver text when available.
3. Check suspicious method and receiver patterns.
4. Return if the call is not suspicious.
5. Determine transaction context:
   - inspect containing function annotations;
   - inspect ancestor lambdas and parent call names.
6. Exclude known database/query calls.
7. Report.

### Finding location

Report on the suspicious external call.

### Message

```text
[YAWN003][HIGH] External call `shippingClient.reserveShipment()` occurs
inside a transaction. External I/O may extend lock duration and create
ambiguous partial-failure behavior.
```

### Required tests

- Reports a client call inside an annotated function.
- Reports a publisher call inside `transaction {}`.
- Reports a nested external call inside transaction scope.
- Does not report the same call outside a transaction.
- Does not report a repository call inside a transaction.
- Does not report a local helper merely named `send`.
- Respects configured transaction annotations.
- Respects configured transaction functions.
- Respects configured receiver regexes.
- Reports the correct source location.

### Known limitations

- Naming-based detection can miss external clients with unusual names.
- Local methods can have suspicious names.
- Static analysis cannot know whether a method performs network I/O.
- Interprocedural transaction propagation is not handled.
- The best remediation depends on consistency requirements.

---

## 12. Prove Detekt plugin loading first

Before implementing real rules, prove that the plugin loads.

Required pieces:

- `RuleSetProvider` implementation.
- Provider registered through `META-INF/services`.
- `doctor-rules` added to the demo module through `detektPlugins(project(...))`.
- A temporary smoke rule that reports a known marker.

### Temporary smoke rule

Report a string literal containing:

```text
YAWN_DOCTOR_TEST
```

Add the marker to the demo source and run:

```bash
./gradlew :demo-codebase:detekt
```

Do not proceed until the finding appears. Then remove the temporary rule and marker.

This isolates plugin-loading problems before AST logic is introduced.

---

## 13. Demo codebase

The demo codebase should compile without a real ORM.

Create a small fake DSL containing enough types and function names to make examples realistic:

```kotlin
annotation class Transactional

class Session {
    fun <T> createYawnCriteria(
        table: Table<T>,
        block: CriteriaScope<T>.() -> Unit,
    ): Query<T> = TODO()
}

class Query<T> {
    fun list(): List<T> = emptyList()
    fun countProjection(): Long = 0
}

class CriteriaScope<T> {
    fun <V> addEq(column: Column<V>, value: V) = Unit
    fun <V> addIn(column: Column<V>, values: Collection<V>) = Unit
}

class Table<T>
class Column<T>
```

The exact implementation can be trivial. The demo exists for source analysis, not runtime execution.

Create realistic examples around:

- brands;
- orders;
- order items;
- fulfillment;
- shipping;
- events.

### Bad fixture requirements

Include one clean example for each rule.

### Good fixture requirements

Include the corresponding safer pattern for each rule.

The dashboard should let the viewer compare risky and safe files.

---

## 14. SARIF output

Configure Detekt to generate SARIF for the demo codebase.

Expected output location may be similar to:

```text
demo-codebase/build/reports/detekt/detekt.sarif
```

Do not hard-code a path in many places. Define it once in a script or package command.

### Normalized frontend schema

```typescript
type DoctorFinding = {
  id: string;
  ruleId: string;
  title: string;
  file: string;
  startLine: number;
  startColumn: number;
  endLine?: number;
  endColumn?: number;
  message: string;
  severity: "error" | "warning" | "info";
  confidence: "high" | "medium" | "low";
  category: string;
  explanation: string;
  impact: string;
  remediations: string[];
  documentationSlug: string;
  source?: string;
};
```

### Normalization requirements

The converter must:

- read SARIF;
- extract rule ID;
- preserve file path;
- preserve source region;
- preserve message;
- enrich findings from a rule catalog;
- optionally attach source text;
- write `dashboard/public/findings.json`;
- fail if no findings exist;
- fail if the three expected rule IDs are missing in the demo.

Suggested package scripts:

```json
{
  "scripts": {
    "report": "tsx scripts/convert-sarif.ts",
    "verify-report": "tsx scripts/verify-report.ts",
    "dev": "next dev"
  }
}
```

---

## 15. Dashboard specification

Build a polished single-page developer-tool interface.

### Required sections

#### Header

Show:

- Yawn Doctor name.
- “Explainable Kotlin ORM analysis.”
- Scan duration if available.
- Total findings.
- Total affected files.
- “Deterministic analysis” badge.

#### Summary cards

Show counts for:

- Database round trips.
- Unnecessary hydration.
- Transaction boundaries.

#### Findings list

Each item shows:

- rule ID;
- title;
- file;
- line;
- severity;
- confidence.

#### Source viewer

Show:

- source code;
- line numbers;
- highlighted finding line;
- enough surrounding context;
- selected file name.

A styled `<pre>` is sufficient. Monaco is optional and should only be used if it does not slow down the build.

#### Finding detail panel

Show:

- evidence;
- explanation;
- impact;
- confidence;
- remediation directions;
- deterministic-analysis label;
- link or anchor to rule documentation.

#### Optional comparison

Show or link to the corresponding safe example.

### UI requirements

- One page.
- No authentication.
- No settings area.
- No repository uploader.
- No unnecessary navigation.
- Works with static JSON.
- Works when AI is unavailable.
- Developer-tool visual style.
- Readable on a laptop screen.

---

## 16. Optional AI remediation

Implement only after the deterministic analyzer, SARIF pipeline, tests, and dashboard work.

### Endpoint

```text
POST /api/remediation
```

### Input

```json
{
  "ruleId": "YAWN001",
  "source": "containing function source",
  "findingLine": 14,
  "ruleExplanation": "query terminal inside iteration",
  "constraints": [
    "Preserve behavior",
    "Do not invent unavailable methods",
    "Return a unified diff",
    "State all assumptions",
    "Do not apply the patch"
  ]
}
```

### Output

```json
{
  "summary": "Batch-load order items before iteration.",
  "assumptions": [
    "Order IDs can be passed to a bulk predicate.",
    "OrderItem exposes orderId."
  ],
  "patch": "--- a/file.kt\n+++ b/file.kt\n...",
  "validationSteps": [
    "Run unit tests.",
    "Run Yawn Doctor again.",
    "Add or run a query-count integration test."
  ]
}
```

### Guardrails

Display this prominently:

> The finding is deterministic. The proposed patch is AI-generated and requires review.

Never auto-apply the patch.

Do not send an entire repository. Send only:

- rule metadata;
- containing function;
- small relevant context;
- explicit constraints.

---

## 17. Phased implementation plan

Do not ask the coding model to build the entire project at once.

Each phase should end with working commands and a checkpoint for human review.

### Phase 0 — Repository inspection and decisions

#### Goal

Confirm the repository state and record implementation assumptions.

#### Tasks

- Inspect all existing files.
- Confirm whether the repository is empty.
- Confirm JDK and Gradle availability.
- Confirm package names.
- Create `docs/learning-notes.md`.
- Record pinned dependency choices.
- Do not write rule logic yet.

#### Acceptance criteria

- Repository structure is understood.
- No unnecessary framework has been added.
- Dependency versions are documented.
- Next step is unambiguous.

#### Learning checkpoint

Write brief notes answering:

- What is Detekt?
- What does a custom Detekt rule receive?
- What is PSI?
- Why is KSP not the primary tool for expression analysis?

---

### Phase 1 — Scaffold and plugin smoke test

#### Goal

Prove custom rule discovery and execution.

#### Tasks

- Create root Gradle files.
- Create `doctor-rules`.
- Create `demo-codebase`.
- Configure Detekt.
- Implement provider registration.
- Add temporary smoke rule.
- Run tests.
- Run Detekt against demo code.
- Remove smoke rule after successful proof.

#### Acceptance criteria

- `./gradlew test` passes.
- `./gradlew :demo-codebase:detekt` succeeds.
- Custom rule output is visible.
- Provider loading is proven.
- No Spring, database, or Docker dependencies exist.

#### Human review pause

Inspect:

- Gradle structure.
- Service-provider file.
- `RuleSetProvider` implementation.
- Detekt configuration.

Do not proceed until these are understood.

---

### Phase 2 — QueryInsideLoop

#### Goal

Implement the flagship rule with strong tests.

#### Tasks

- Add AST helper functions.
- Detect query terminals.
- Detect query origins.
- Detect loop ancestors.
- Detect collection-iteration lambdas.
- Add positive tests.
- Add negative tests.
- Add demo fixtures.
- Add metadata.
- Update documentation.

#### Acceptance criteria

- At least 8 focused tests.
- All tests pass.
- Correct source location.
- No finding for ordinary collections.
- Demo SARIF contains `YAWN001`.
- Known limitations documented.

#### Learning checkpoint

Inspect PSI shapes for:

- call expression;
- dot-qualified expression;
- lambda expression;
- for loop;
- parent traversal.

Record one surprising AST detail in `docs/learning-notes.md`.

---

### Phase 3 — MaterializedCount

#### Goal

Detect obvious count-after-materialization patterns.

#### Tasks

- Match `.list().size`.
- Match `.list().count()`.
- Verify query origin.
- Handle multiline syntax.
- Add tests.
- Add risky and safe fixtures.
- Update metadata and docs.

#### Acceptance criteria

- At least 7 focused tests.
- All tests pass.
- Ordinary collections are ignored.
- Correct source location.
- Demo SARIF contains `YAWN002`.
- Message does not invent a framework API.

#### Human review pause

Inspect why a hybrid PSI plus local text-shape match is acceptable here.

---

### Phase 4 — ExternalCallInsideTransaction

#### Goal

Implement the transaction-boundary rule conservatively.

#### Tasks

- Detect annotation-based transaction scope.
- Detect lambda-based transaction scope.
- Add configurable receiver regexes.
- Add configurable suspicious methods.
- Exclude obvious repository/query calls.
- Add tests.
- Add realistic fulfillment fixture.
- Document false positives.

#### Acceptance criteria

- At least 9 focused tests.
- All tests pass.
- Calls outside transactions are ignored.
- Query/repository calls are ignored.
- Configuration is covered by tests.
- Correct source location.
- Demo SARIF contains `YAWN003`.

#### Learning checkpoint

Write brief answers:

- Why can network I/O inside a transaction be dangerous?
- What happens when external I/O succeeds but commit fails?
- How does a transactional outbox help?
- Why is idempotency relevant to retries?

---

### Phase 5 — SARIF normalization

#### Goal

Convert analyzer output into stable dashboard input.

#### Tasks

- Enable SARIF report.
- Inspect actual SARIF structure.
- Implement converter.
- Add metadata catalog.
- Attach source text or source paths.
- Add verification script.

#### Acceptance criteria

- One command regenerates SARIF.
- One command generates `findings.json`.
- All three rule IDs are present.
- File names and line numbers are correct.
- Verification fails when expected findings disappear.

#### Human review pause

Open the SARIF manually and identify:

- tool metadata;
- result;
- rule ID;
- artifact location;
- source region;
- message.

---

### Phase 6 — Dashboard

#### Goal

Make the project instantly understandable.

#### Tasks

- Scaffold Next.js.
- Load static JSON.
- Build summary.
- Build findings list.
- Build source viewer.
- Highlight selected line.
- Build detail panel.
- Add filters or category grouping.
- Add deterministic-analysis labels.
- Add safe-example comparison if time permits.

#### Acceptance criteria

- Dashboard runs locally.
- All three findings are selectable.
- Source locations are visually accurate.
- A new viewer can understand each finding without reading the README.
- No live analyzer server is required.
- No AI dependency exists.

---

### Phase 7 — Documentation and demo polish

#### Goal

Make the project interview-ready.

#### Tasks

- Complete README.
- Add architecture diagram.
- Complete rule docs.
- Complete false-positive docs.
- Write 60-second demo script.
- Add screenshot or GIF.
- Add one top-level command or documented sequence.
- Clean generated files and naming.

#### Acceptance criteria

- README begins with problem and demo.
- Installation instructions are not first.
- Limitations are candid.
- Commands are verified.
- Demo takes under 90 seconds.
- Repository has no dead experimental code.

---

### Phase 8 — Optional extension

Choose exactly one:

- AI remediation.
- GitHub SARIF upload.
- Collection-join duplication rule.
- Type resolution.

Do not start an extension while required MVP work is incomplete.

---

## 18. Commands to aim for

The exact commands may vary, but the repository should converge toward:

```bash
# Run analyzer tests
./gradlew test

# Analyze demo code and generate SARIF
./gradlew :demo-codebase:detekt

# Convert SARIF for the dashboard
cd dashboard
pnpm install
pnpm report
pnpm verify-report

# Run dashboard
pnpm dev
```

A root helper script or Gradle task may later combine these:

```bash
./scripts/demo.sh
```

or:

```bash
./gradlew scanDemo
```

Do not create a complex task orchestration framework.

---

## 19. Coding harness operating contract

Prepend this to implementation prompts:

```text
You are implementing a tightly scoped portfolio project.

Operating rules:

1. Inspect the existing repository before editing files.
2. Preserve working architecture unless a concrete error requires a change.
3. Do not add Spring, Hibernate runtime setup, Docker, or a database.
4. Make the smallest change that satisfies the current milestone.
5. Run relevant tests after every implementation step.
6. Do not claim completion until commands actually pass.
7. When an API does not match expectations, inspect the installed dependency
   instead of guessing method names.
8. Preserve precise source locations in all findings.
9. Prefer deterministic PSI/AST logic over regex scans across whole files.
10. Small local text checks are allowed only after PSI has narrowed the node.
11. Document known false positives and false negatives.
12. Do not implement automatic correction for semantic database changes.
13. Do not add optional features before required acceptance criteria pass.
14. Keep functions small and name AST helpers according to their purpose.
15. Stop at the end of the requested milestone and summarize:
    - files changed;
    - commands run;
    - results;
    - known issues;
    - recommended next step.
```

---

## 20. Starter prompt for the coding harness

Use this prompt to begin Phase 1:

```text
Read PLAN.md completely before changing files.

Implement only Phase 1: Scaffold and plugin smoke test.

Create a Kotlin Gradle multi-module project with:

- doctor-rules
- demo-codebase

Use:

- JDK 17
- Kotlin 2.0.21
- Detekt 1.23.8

Requirements:

1. doctor-rules is a custom Detekt plugin.
2. Add detekt-api as compileOnly.
3. Add the appropriate Detekt test dependency.
4. Implement a RuleSetProvider named YawnDoctorProvider.
5. Register it through the required META-INF/services file.
6. Load doctor-rules from demo-codebase through detektPlugins(project(...)).
7. Implement one temporary smoke rule that reports a string literal containing
   YAWN_DOCTOR_TEST.
8. Add one demo Kotlin file containing that marker.
9. Run the custom rule through :demo-codebase:detekt.
10. Add one test for the smoke rule.
11. Do not implement the real three rules.
12. Do not add Spring, Hibernate, KSP, Docker, a database, a web server, or the
    dashboard.

Before stopping:

- run the relevant Gradle test command;
- run :demo-codebase:detekt;
- fix all errors;
- show the exact commands and their outcomes;
- list every created file;
- explain how the service-provider registration causes Detekt to discover the
  custom rule set;
- identify anything I should inspect manually before continuing.

Do not continue to Phase 2.
```

---

## 21. Prompt for QueryInsideLoop

```text
Read PLAN.md and inspect the existing implementation.

Implement only Phase 2: QueryInsideLoop.

Rule ID: YAWN001
Rule name: QueryInsideLoop

Required behavior:

- Report query terminal calls named list, first, single, or count.
- Report only when a configured query constructor named query,
  createYawnCriteria, or createCriteria exists in the same receiver chain.
- Report only when the terminal executes inside:
  - for;
  - while;
  - forEach;
  - map;
  - flatMap;
  - associate;
  - onEach.
- Report on the terminal call.
- Do not perform variable tracking.
- Do not perform interprocedural analysis.
- Do not report ordinary collection operations.
- Add configuration support for constructors, terminals, and iteration
  functions.
- Add at least 8 focused tests.
- Add one risky and one safe demo example.
- Update rule metadata and documentation.

Implementation constraints:

- Use PSI/AST traversal.
- Do not scan whole source files with regular expressions.
- Extract reusable helpers for query-origin detection and loop context.
- Keep the rule conservative.
- Do not implement auto-correction.

Before stopping:

- run all rule tests;
- run demo-codebase Detekt;
- confirm YAWN001 appears in SARIF or console output;
- report files changed, commands run, results, limitations, and next step.

Do not begin MaterializedCount.
```

---

## 22. Prompt for MaterializedCount

```text
Read PLAN.md and inspect the existing implementation.

Implement only Phase 3: MaterializedCount.

Rule ID: YAWN002
Rule name: MaterializedCount

Required matches:

- <recognized query>.list().size
- <recognized query>.list().count()

A recognized query must contain one configured constructor in the receiver
chain:

- query
- createYawnCriteria
- createCriteria

Requirements:

- Visit the relevant PSI expression.
- It is acceptable to use a small local expression-text shape check after PSI
  has identified the candidate node.
- Separately verify the query origin.
- Report on size or count.
- Support multiline call chains.
- Ignore ordinary collection sizes and counts.
- Recommend a database-level count projection or dedicated count query.
- Do not invent an exact Yawn API.
- Do not auto-correct.
- Add at least 7 focused tests.
- Add risky and safe demo fixtures.
- Update rule metadata and documentation.

Before stopping:

- run all tests;
- run demo-codebase Detekt;
- confirm YAWN001 and YAWN002 appear;
- report commands, results, limitations, and next step.

Do not begin ExternalCallInsideTransaction.
```

---

## 23. Prompt for ExternalCallInsideTransaction

```text
Read PLAN.md and inspect the existing implementation.

Implement only Phase 4: ExternalCallInsideTransaction.

Rule ID: YAWN003
Rule name: ExternalCallInsideTransaction

Transaction context is established by either:

1. A containing function with a configured annotation such as Transactional.
2. A containing lambda passed to a configured function such as transaction,
   inTransaction, or withTransaction.

A call is suspicious when its receiver or method matches configuration.

Default receiver regexes:

- .*Client
- .*Publisher
- .*Producer
- httpClient

Default method names:

- send
- publish
- post
- execute
- reserve
- notify
- sleep

Requirements:

- Report only when both transaction context and suspicious-call detection are
  satisfied.
- Exclude recognized ORM query calls.
- Avoid reporting obvious repository calls.
- Report on the suspicious call.
- Include a message about longer-held locks and ambiguous partial failure.
- Mention moving I/O, idempotency, and transactional outbox only as
  remediation directions.
- Add at least 9 focused tests.
- Test configuration behavior.
- Add a realistic fulfillment demo.
- Update rule metadata and documentation.

Keep the implementation conservative and syntax-based. Do not attempt call
graph or runtime I/O analysis.

Before stopping:

- run all tests;
- run demo-codebase Detekt;
- confirm YAWN001, YAWN002, and YAWN003 appear;
- report commands, results, false positives, false negatives, and next step.

Do not begin the dashboard.
```

---

## 24. Learning breadcrumbs

Pause after each phase and inspect the concepts below.

### Kotlin PSI

Understand:

- `KtCallExpression`
- `KtDotQualifiedExpression`
- `KtLambdaExpression`
- `KtForExpression`
- `KtWhileExpression`
- `KtNamedFunction`
- annotations
- parent traversal
- source offsets and line mapping

Questions:

- What syntax does each PSI node own?
- Where does a receiver live?
- How is a trailing lambda represented?
- When is a call chain one node versus several nested nodes?
- Why can source text look simple while the PSI tree is nested?

### Static-analysis design

Understand:

- syntax analysis;
- type-aware analysis;
- data-flow analysis;
- interprocedural analysis;
- control-flow graph;
- false positive;
- false negative;
- confidence.

Questions:

- Which claims can this rule prove?
- Which claims are only heuristics?
- What extra information would type resolution provide?
- When should a rule be a warning rather than an error?
- Why are precise source locations important?

### Database engineering

Understand:

- N+1 queries;
- entity hydration;
- scalar projection;
- transaction duration;
- lock duration;
- retries;
- idempotency;
- transactional outbox;
- partial failure.

Questions:

- What does the application pay for when hydrating entities?
- Why is one query per loop iteration dangerous?
- Why can external I/O amplify database contention?
- What happens when an external call succeeds and the transaction rolls back?

### Tooling architecture

Understand:

- Detekt plugin loading;
- Java `ServiceLoader`;
- SARIF;
- static report generation;
- CI annotations;
- analyzer/frontend separation.

Questions:

- Why is SARIF a useful boundary?
- Why should the dashboard not execute the analyzer?
- What would stable fingerprints improve?
- Why is a custom Detekt plugin more credible than a regex script?

---

## 25. Documentation requirements

### `docs/architecture.md`

Explain:

- Why Detekt.
- Why not KSP.
- Why not an IntelliJ plugin for the MVP.
- Why SARIF separates analysis from presentation.
- Why deterministic detection is separated from AI remediation.
- Why the demo codebase uses a fake DSL.

### `docs/rule-design.md`

For each rule include:

- Intent.
- Matched syntax.
- Deliberately unmatched syntax.
- Confidence.
- Violation example.
- Safe direction.
- Remediation options.
- Test cases.
- Known limitations.

### `docs/false-positives.md`

Be explicit:

- Queries hidden behind helpers are missed.
- MaterializedCount cannot know real result-set size.
- External-call detection partly relies on naming.
- No interprocedural analysis exists.
- No runtime query or lock measurement exists.
- The tool identifies risky patterns, not guaranteed production incidents.

### `docs/ai-remediation.md`

Explain:

- AI cannot create findings.
- AI sees limited context.
- Patches are never applied automatically.
- Assumptions must be displayed.
- Deterministic analysis is rerun after changes.
- Tests and human review remain required.

### `docs/learning-notes.md`

After each phase record:

- What was expected.
- What the AST or API actually looked like.
- One surprising detail.
- One false-positive risk.
- One decision changed.
- What a more advanced version could do.

### `docs/demo-script.md`

Keep the final demo under 90 seconds.

---

## 26. README outline

The README should begin with the product and demo.

```text
# Yawn Doctor

Explainable static analysis for risky Kotlin ORM patterns.

[GIF or screenshot]

## What it detects

- Query execution inside loops
- Counting by materializing ORM entities
- External I/O inside database transactions

## Why it exists

Database performance and reliability problems are often introduced through
ordinary-looking application code. Yawn Doctor turns a small number of
high-value engineering practices into repeatable code-review-time checks.

## Demo

[Commands]

## Example finding

[Source and output]

## How it works

Kotlin source
→ Detekt PSI traversal
→ deterministic findings
→ SARIF
→ GitHub annotations and local dashboard
→ optional AI remediation

## Rule catalog

## Architecture

## Known limitations

## Development

## Future work
```

Do not lead with installation instructions.

---

## 27. Demo script

Suggested 60-second flow:

1. Open dashboard.

   “Yawn Doctor is a Detekt plugin for high-impact ORM and transaction risks in Kotlin services.”

2. Select QueryInsideLoop.

   “This `list()` call is nested inside `forEach`, so database round trips can grow with the number of orders.”

3. Show evidence and confidence.

   “The finding is deterministic and reports the exact syntax evidence.”

4. Select MaterializedCount.

   “This code hydrates full entities only to calculate a scalar count.”

5. Select ExternalCallInsideTransaction.

   “This shipping call happens while a transaction is open, potentially increasing lock duration and creating partial-failure ambiguity.”

6. Show SARIF or GitHub annotation.

   “The same analyzer can run locally, in CI, or through GitHub code scanning.”

7. Optional AI feature.

   “AI can propose a reviewable patch, but it cannot invent or suppress findings.”

---

## 28. Definition of done

The first version is done when all of these are true:

- [ ] Three deterministic rules exist.
- [ ] Each rule has focused positive and negative tests.
- [ ] All tests pass.
- [ ] Findings have accurate file and line locations.
- [ ] Demo code includes risky and safe examples.
- [ ] Detekt generates SARIF.
- [ ] SARIF normalization succeeds.
- [ ] Dashboard displays all findings.
- [ ] Dashboard works without AI.
- [ ] Known limitations are documented.
- [ ] Architecture is documented.
- [ ] One command or short command sequence regenerates the demo.
- [ ] Demo can be delivered in under 90 seconds.
- [ ] No database or runtime ORM infrastructure is required.
- [ ] No dead experimental code remains.
- [ ] README contains a screenshot or GIF.
- [ ] The project can be explained in one sentence.

Suggested one-sentence explanation:

> Yawn Doctor is an explainable Detekt plugin that catches risky Kotlin ORM and transaction patterns, exports precise SARIF annotations, and optionally generates constrained, reviewable refactoring proposals.

---

## 29. Backlog after the MVP

Prioritize in this order:

1. Add type resolution to distinguish real ORM types from lookalike methods.
2. Add stable SARIF fingerprints.
3. Add baseline support so existing findings do not block adoption.
4. Add GitHub PR summaries for newly introduced findings.
5. Build a small evaluation corpus and track rule precision.
6. Add the collection-join duplicate-root rule.
7. Add safe IDE quick fixes for purely syntactic cases.
8. Test against a real open-source Kotlin/Hibernate codebase.
9. Generate frontend rule metadata from one shared source.
10. Publish the rule set as a reusable package.

Do not treat the backlog as part of the initial definition of done.
