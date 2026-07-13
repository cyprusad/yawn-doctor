export type FindingSeverity = "error" | "warning" | "info"
export type FindingConfidence = "high" | "medium" | "low"
export type FindingCategory =
  | "DATABASE_ROUND_TRIPS"
  | "UNNECESSARY_HYDRATION"
  | "TRANSACTION_BOUNDARIES"

export interface DoctorFinding {
  id: string
  ruleId: string
  title: string
  file: string
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
  message: string
  severity: FindingSeverity
  confidence: FindingConfidence
  category: FindingCategory
  explanation: string
  impact: string
  remediations: string[]
  documentationSlug: string
  source?: string
}

export interface ReportOutput {
  /** Normalized findings, ordered by file and line. */
  findings: DoctorFinding[]
  /** Source text for each affected file, keyed by relative file path. */
  files: Record<string, string>
}
