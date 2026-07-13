/**
 * convert-sarif.ts
 *
 * Reads the detekt SARIF report from the demo codebase and writes a
 * frontend-friendly findings.json to dashboard/public/.
 *
 * Usage:
 *   pnpm report
 *   pnpm report -- ../other/report.sarif
 */

import { readFileSync, writeFileSync, existsSync } from "node:fs"
import { resolve, relative, dirname } from "node:path"
import { fileURLToPath } from "node:url"
import { ruleCatalog } from "../lib/rules.js"
import type { DoctorFinding, ReportOutput, FindingCategory, FindingSeverity } from "../lib/types.js"

const __dirname = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(__dirname, "../..")

const defaultSarifPath = resolve(
  projectRoot,
  "demo-codebase/build/reports/detekt/main.sarif",
)

function fail(message: string): never {
  console.error(`error: ${message}`)
  process.exit(1)
}

/** Convert a `file://` URI to a local absolute path. */
function uriToPath(uri: string): string {
  // file:///Users/... -> /Users/...
  if (!uri.startsWith("file://")) fail(`unexpected artifact URI: ${uri}`)
  return uri.slice(7) // strip "file://"
}

/** Strip the YAWN prefix from the message to get the description. */
function stripPrefix(text: string): string {
  // "[YAWN001][HIGH] Some message." -> "Some message."
  return text.replace(/^\[YAWN\d+\]\[\w+\]\s*/, "")
}

/** Resolve rule catalog entry from a full SARIF ruleId like "detekt.YawnDoctor.QueryInsideLoop". */
function lookupRule(sarifRuleId: string) {
  const shortName = sarifRuleId.split(".").pop()
  if (!shortName) fail(`cannot extract short rule name from ${sarifRuleId}`)
  const entry = ruleCatalog[shortName]
  if (!entry) fail(`unknown Yawn Doctor rule: ${shortName} (from ${sarifRuleId})`)
  return entry
}

function main() {
  const sarifPath = process.argv[2] ?? defaultSarifPath

  if (!existsSync(sarifPath)) {
    fail(`SARIF report not found at ${sarifPath}\n  Run ./gradlew :demo-codebase:detektMain first.`)
  }

  const sarif = JSON.parse(readFileSync(sarifPath, "utf-8"))
  const run = sarif.runs?.[0]
  if (!run?.results) fail("SARIF file has no runs[0].results")

  const findings: DoctorFinding[] = []
  const affectedFiles = new Set<string>()

  for (const result of run.results) {
    const loc = result.locations?.[0]?.physicalLocation
    if (!loc) continue

    const uri = loc.artifactLocation?.uri
    if (!uri) continue
    const filePath = uriToPath(uri)
    const relPath = relative(projectRoot, filePath)

    const region = loc.region
    const entry = lookupRule(result.ruleId)
    const cleanMessage = stripPrefix(result.message?.text ?? "")

    const findingId = [
      entry.yawnId,
      relPath.replace(/[/. ]/g, "-"),
      region.startLine,
      region.startColumn,
    ].join("_")

    affectedFiles.add(relPath)

    findings.push({
      id: findingId,
      ruleId: entry.yawnId,
      title: entry.title,
      file: relPath,
      startLine: region.startLine,
      startColumn: region.startColumn,
      endLine: region.endLine ?? region.startLine,
      endColumn: region.endColumn ?? region.startColumn,
      message: cleanMessage,
      severity: entry.severity,
      confidence: entry.confidence,
      category: entry.category,
      explanation: entry.explanation,
      impact: entry.impact,
      remediations: entry.remediations,
      documentationSlug: entry.documentationSlug,
    })
  }

  if (findings.length === 0) {
    fail("No Yawn Doctor findings found in SARIF report.")
  }

  // Attach source text for each affected file.
  const files: Record<string, string> = {}
  for (const relPath of affectedFiles) {
    const absPath = resolve(projectRoot, relPath)
    files[relPath] = existsSync(absPath) ? readFileSync(absPath, "utf-8") : ""
  }

  const output: ReportOutput = { findings, files }

  const outDir = resolve(projectRoot, "dashboard/public")
  const outPath = resolve(outDir, "findings.json")
  writeFileSync(outPath, JSON.stringify(output, null, 2), "utf-8")

  const ruleIds = [...new Set(findings.map((f) => f.ruleId))].sort()
  console.log(`✓ wrote ${findings.length} finding(s) to ${outPath}`)
  console.log(`  rules: ${ruleIds.join(", ")}`)
  console.log(`  files: ${affectedFiles.size}`)
}

main()
