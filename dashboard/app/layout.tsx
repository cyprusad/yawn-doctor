import type { Metadata } from "next"
import "./globals.css"

export const metadata: Metadata = {
  title: "Yawn Doctor — Kotlin ORM Analysis",
  description: "Explainable static analysis for risky Kotlin ORM patterns",
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}
