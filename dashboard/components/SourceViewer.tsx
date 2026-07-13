"use client"

import { Highlight, themes, type PrismTheme } from "prism-react-renderer"

interface Props {
  file: string
  source: string
  startLine: number
  endLine: number
}

// Custom dark theme matching the dashboard palette
const theme: PrismTheme = {
  ...themes.nightOwl,
  plain: {
    color: "#e6edf3",
    backgroundColor: "transparent",
  },
}

export default function SourceViewer({ file, source, startLine, endLine }: Props) {
  return (
    <>
      <div className="source-header">{file}</div>
      <div className="source-code">
        <Highlight code={source.trimEnd()} language="kotlin" theme={theme}>
          {({ tokens, getTokenProps }) => (
            <>
              {tokens.map((line, i) => {
              const lineNum = i + 1
              const highlight = lineNum >= startLine && lineNum <= endLine
              return (
                <div
                  key={i}
                  className={`source-line${highlight ? " highlight" : ""}`}
                >
                  <span className="line-num">{lineNum}</span>
                  <span className="line-code">
                    {line.map((token, key) => (
                      <span key={key} {...getTokenProps({ token })} />
                    ))}
                  </span>
                </div>
              )
            })}
            </>
          )}
        </Highlight>
      </div>
    </>
  )
}
