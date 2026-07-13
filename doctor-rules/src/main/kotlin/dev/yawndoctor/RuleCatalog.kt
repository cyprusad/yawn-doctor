package dev.yawndoctor

import dev.yawndoctor.model.DoctorRuleDescriptor

object RuleCatalog {

    val yawn001 = DoctorRuleDescriptor(
        id = "YAWN001",
        title = "Query inside loop",
        category = "DATABASE_ROUND_TRIPS",
        severity = "warning",
        confidence = "high",
        summary = "A recognized ORM query terminal executes inside an iteration construct.",
        impact = "Database round trips may grow linearly with input size.",
        remediations = listOf(
            "Collect identifiers before the loop.",
            "Fetch matching rows in one bulk query.",
            "Group results in memory by foreign key.",
        ),
        documentationSlug = "yawn001-query-inside-loop",
    )

    val all = listOf(yawn001)
}
