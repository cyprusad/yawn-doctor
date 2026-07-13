import type { NextConfig } from "next"

const nextConfig: NextConfig = {
  output: "export",
  distDir: "out",
  turbopack: {
    root: process.cwd(),
  },
}

export default nextConfig
