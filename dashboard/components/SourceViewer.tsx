"use client"

interface Props {
  file: string
  source: string
  startLine: number
  endLine: number
}

export default function SourceViewer({ file, source, startLine, endLine }: Props) {
  const lines = source.split("\n")

  return (
    <>
      <div className="source-header">{file}</div>
      <div className="source-code">
        {lines.map((text, i) => {
          const lineNum = i + 1
          const highlight = lineNum >= startLine && lineNum <= endLine
          return (
            <div key={i} className={`source-line${highlight ? " highlight" : ""}`}>
              <span className="line-num">{lineNum}</span>
              <span className="line-code">{text}</span>
            </div>
          )
        })}
      </div>
    </>
  )
}
