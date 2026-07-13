import type { FindingCategory, FindingConfidence, FindingSeverity } from "./types.js"

export interface RuleCatalogEntry {
  ruleId: string
  yawnId: string
  title: string
  category: FindingCategory
  severity: FindingSeverity
  confidence: FindingConfidence
  explanation: string
  impact: string
  remediations: string[]
  documentationSlug: string
}

export const ruleCatalog: Record<string, RuleCatalogEntry> = {
  "QueryInsideLoop": {
    ruleId: "QueryInsideLoop",
    yawnId: "YAWN001",
    title: "Query inside loop",
    category: "DATABASE_ROUND_TRIPS",
    severity: "warning",
    confidence: "high",
    explanation:
      "A recognized ORM query terminal (list, first, single, count) executes inside " +
      "a for loop, while loop, or collection-iteration lambda such as forEach or map. " +
      "Each iteration may issue a separate database round trip.",
    impact:
      "Database round trips grow linearly with the number of input elements. " +
      "Even with a small result per query, the cumulative latency and connection " +
      "overhead can degrade throughput and increase database load.",
    remediations: [
      "Collect identifiers before the loop and fetch matching rows in one bulk query.",
      "Use a batch-loading strategy such as Hibernate's IN clause or sub-select fetching.",
      "Restructure the code so the query is not inside the iteration.",
    ],
    documentationSlug: "yawn001-query-inside-loop",
  },

  "MaterializedCount": {
    ruleId: "MaterializedCount",
    yawnId: "YAWN002",
    title: "Materialized count",
    category: "UNNECESSARY_HYDRATION",
    severity: "warning",
    confidence: "high",
    explanation:
      "A query result is fully materialized (via .list()) and then counted in memory " +
      "with .size or .count(). Loading all entities when only a count is needed wastes " +
      "memory, bandwidth, and CPU on hydration.",
    impact:
      "The application may transfer and hydrate many entities unnecessarily. " +
      "For large result sets this can cause memory pressure, slow response times, " +
      "and increased database I/O for data that is never used.",
    remediations: [
      "Use a database-level count projection (e.g., SELECT COUNT(*)) instead of loading entities.",
      "Use a dedicated count query that returns a scalar Long.",
      "When an ORM API provides countProjection(), prefer it over list().size.",
    ],
    documentationSlug: "yawn002-materialized-count",
  },

  "CollectionJoinWithoutDistinct": {
    ruleId: "CollectionJoinWithoutDistinct",
    yawnId: "YAWN004",
    title: "Collection join without distinct",
    category: "ENTITY_QUERY",
    severity: "warning",
    confidence: "medium",
    explanation:
      "An entity query joins a collection association (OneToMany/ManyToMany) and calls " +
      ".list() without deduplication. Collection joins can return the same root entity " +
      "multiple times — once per joined child row. Callers that iterate the result " +
      "to delete, update, or count parents will see duplicates.",
    impact:
      "Duplicate root entities cause overfetching (duplicate parent rows across the wire), " +
      "extra hydration work, and application-level bugs when iterating for deletion, " +
      "updates, or counting. Every call site must remember to dedup, which is fragile.",
    remediations: [
      "Add .distinctBy { it.id } after .list() to deduplicate in application code.",
      "Use .distinctRootEntity() when the ORM API provides it (e.g. Hibernate's DISTINCT_ROOT_ENTITY result transformer).",
      "Consider whether a join is necessary at all for the use case.",
    ],
    documentationSlug: "yawn004-collection-join-without-distinct",
  },

  "ExternalCallInsideTransaction": {
    ruleId: "ExternalCallInsideTransaction",
    yawnId: "YAWN003",
    title: "External call inside transaction",
    category: "TRANSACTION_BOUNDARIES",
    severity: "warning",
    confidence: "medium",
    explanation:
      "A call matching configured external I/O patterns (receiver name, method name) " +
      "occurs inside a database transaction, either via a @Transactional annotation " +
      "or a lambda passed to a transaction function such as transaction, open, or " +
      "inTransaction. External I/O inside a transaction extends lock duration and " +
      "creates ambiguous partial-failure states.",
    impact:
      "Locks may be held longer than necessary, increasing contention and deadlock risk. " +
      "If the external call succeeds but the transaction later rolls back, the system " +
      "reaches an inconsistent state that may require manual compensation.",
    remediations: [
      "Move external I/O outside the transaction when it is safe to do so.",
      "Commit the database transaction before calling the external system.",
      "Use a transactional outbox pattern: write an event to a database table within " +
      "the transaction, then asynchronously publish from the outbox.",
      "Ensure external operations are idempotent so retries are safe.",
    ],
    documentationSlug: "yawn003-external-call-inside-transaction",
  },
}
