package dev.yawndoctor.model

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
