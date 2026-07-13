"use client"

import type { DoctorFinding } from "../lib/types"

interface Props {
  finding: DoctorFinding
}

export default function DetailPanel({ finding }: Props) {
  return (
    <div className="detail-panel">
      <h2>
        <span>{finding.title}</span>
        <span className="badge-det">deterministic</span>
      </h2>

      <div className="detail-meta">
        <span>
          <span className={`badge-confidence ${finding.confidence}`}>{finding.confidence}</span>
        </span>
        <span>{finding.file}:{finding.startLine}:{finding.startColumn}</span>
        <span>{finding.ruleId}</span>
      </div>

      <div className="detail-section">
        <h3>Finding</h3>
        <p>{finding.message}</p>
      </div>

      <div className="detail-section">
        <h3>Explanation</h3>
        <p>{finding.explanation}</p>
      </div>

      <div className="detail-section">
        <h3>Impact</h3>
        <p>{finding.impact}</p>
      </div>

      <div className="detail-section">
        <h3>Remediation</h3>
        <ul>
          {finding.remediations.map((r, i) => (
            <li key={i}>{r}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}
