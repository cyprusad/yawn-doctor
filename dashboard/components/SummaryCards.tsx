"use client"

import type { DoctorFinding } from "../lib/types"

interface Props {
  findings: DoctorFinding[]
  onFilter: (category: string | null) => void
  activeFilter: string | null
}

const cardConfig: Record<string, { label: string; color: string }> = {
  DATABASE_ROUND_TRIPS: { label: "Database round trips", color: "var(--red)" },
  UNNECESSARY_HYDRATION: { label: "Unnecessary hydration", color: "var(--orange)" },
  TRANSACTION_BOUNDARIES: { label: "Transaction boundaries", color: "var(--purple)" },
  ENTITY_QUERY: { label: "Entity query", color: "var(--yellow)" },
}

export default function SummaryCards({ findings, onFilter, activeFilter }: Props) {
  const counts: Record<string, number> = {}
  for (const f of findings) {
    counts[f.category] = (counts[f.category] || 0) + 1
  }

  return (
    <div className="summary-bar">
      {Object.entries(cardConfig).map(([key, cfg]) => {
        const count = counts[key] || 0
        const active = activeFilter === key
        return (
          <div
            key={key}
            className={`summary-card${active ? " active" : ""}`}
            onClick={() => onFilter(active ? null : key)}
          >
            <div className="count" style={{ color: cfg.color }}>
              {count}
            </div>
            <div className="label">{cfg.label}</div>
          </div>
        )
      })}
    </div>
  )
}
