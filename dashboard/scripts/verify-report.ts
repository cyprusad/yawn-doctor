/**
 * verify-report.ts
 *
 * Reads findings.json and verifies that all three expected Yawn Doctor
 * rule IDs (YAWN001, YAWN002, YAWN003) are present. Exits non-zero if
 * any are missing.
 *
 * Usage:
 *   pnpm verify-report
 *   pnpm verify-report -- path/to/findings.json
 */

import { readFileSync, existsSync } from "node:fs"
import { resolve, dirname } from "node:path"
import { fileURLToPath } from "node:url"

const __dirname = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(__dirname, "../..")
const defaultJsonPath = resolve(projectRoot, "dashboard/public/findings.json")

const expectedRuleIds = ["YAWN001", "YAWN002", "YAWN003"]

function fail(message: string): never {
  console.error(`FAIL: ${message}`)
  process.exit(1)
}

function main() {
  const jsonPath = process.argv[2] ?? defaultJsonPath

  if (!existsSync(jsonPath)) {
    fail(`findings.json not found at ${jsonPath}\n  Run pnpm report first.`)
  }

  const report = JSON.parse(readFileSync(jsonPath, "utf-8"))
  const findings = report.findings
  if (!Array.isArray(findings)) fail("report.findings is not an array")

  const present = new Set(findings.map((f: { ruleId: string }) => f.ruleId))
  const missing = expectedRuleIds.filter((id) => !present.has(id))

  if (missing.length > 0) {
    fail(
      `Missing expected rule ID(s): ${missing.join(", ")}\n` +
        `  present: ${[...present].sort().join(", ")}`,
    )
  }

  console.log(`✓ verification passed — all ${expectedRuleIds.join(", ")} present`)
  console.log(`  total findings: ${findings.length}`)
}

main()
