"use client"

import { useEffect, useState } from "react"
import type { DoctorFinding, ReportOutput } from "../lib/types"
import SummaryCards from "../components/SummaryCards"
import SourceViewer from "../components/SourceViewer"
import DetailPanel from "../components/DetailPanel"

interface GroupedFile {
  path: string
  findings: DoctorFinding[]
}

export default function Home() {
  const [data, setData] = useState<ReportOutput | null>(null)
  const [selected, setSelected] = useState<DoctorFinding | null>(null)
  const [filter, setFilter] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)

  useEffect(() => {
    fetch("/findings.json")
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status} — run ./gradlew yawnDoctorReport first`)
        return r.json()
      })
      .then((d: ReportOutput) => setData(d))
      .catch((e: Error) => setErr(e.message))
  }, [])

  if (err) {
    return (
      <div className="empty-state" style={{ height: "100vh", flexDirection: "column", gap: "0.5rem" }}>
        <div style={{ fontSize: "2rem" }}>⚠️</div>
        <div>{err}</div>
      </div>
    )
  }

  if (!data) {
    return <div className="empty-state" style={{ height: "100vh" }}>Loading…</div>
  }

  const { findings, files } = data

  const filtered = filter ? findings.filter((f) => f.category === filter) : findings

  const groups: GroupedFile[] = []
  const seen = new Set<string>()
  for (const f of filtered) {
    const key = f.file
    if (!seen.has(key)) {
      seen.add(key)
      groups.push({
        path: key,
        findings: filtered.filter((ff) => ff.file === key),
      })
    }
  }

  return (
    <>
      <header className="header">
        <h1>
          <span className="logo">🏥</span> Yawn Doctor
          <span style={{ marginLeft: "auto", fontSize: "0.75rem", fontWeight: 400, color: "var(--text-muted)" }}>
            {findings.length} finding{findings.length !== 1 ? "s" : ""} · {Object.keys(files).length} file{Object.keys(files).length !== 1 ? "s" : ""}
          </span>
        </h1>
        <div className="subtitle">Explainable static analysis for risky Kotlin ORM patterns</div>
      </header>

      <SummaryCards findings={findings} onFilter={setFilter} activeFilter={filter} />

      <div className="main">
        <aside className="sidebar">
          <div className="sidebar-header">Findings</div>
          {groups.map((g) => (
            <div key={g.path} className="file-group">
              <div className="file-head">
                <span>{g.path.split("/").pop()}</span>
                <span className="file-count">{g.findings.length}</span>
              </div>
              {g.findings.map((f) => (
                <div
                  key={f.id}
                  className={`finding-item${selected?.id === f.id ? " selected" : ""}`}
                  onClick={() => setSelected(f)}
                >
                  <span className={`rule-badge yawn${f.ruleId.toLowerCase()}`}>{f.ruleId}</span>
                  <span className="finding-line">{f.startLine}:{f.startColumn}</span>
                  <span className="finding-msg">{f.message}</span>
                </div>
              ))}
            </div>
          ))}
        </aside>

        <div className="content">
          <div className="source-panel">
            {selected ? (
              <SourceViewer
                file={selected.file}
                source={files[selected.file] ?? ""}
                startLine={selected.startLine - 2}
                endLine={selected.endLine + 2}
              />
            ) : (
              <div className="empty-state">Select a finding to view source</div>
            )}
          </div>

          {selected && <DetailPanel finding={selected} />}
        </div>
      </div>

      <div className="footer">
        <span>Yawn Doctor — explainable Kotlin ORM analysis</span>
        <span className="badge-det">deterministic analysis</span>
      </div>
    </>
  )
}
