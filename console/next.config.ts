import type { NextConfig } from 'next'

const basePath = process.env.TROQUIM_CONSOLE_BASE_PATH || ''

const nextConfig: NextConfig = {
  output: 'standalone',
  basePath,
  env: {
    NEXT_PUBLIC_TROQUIM_CONSOLE_BASE_PATH: basePath,
  },
}

export default nextConfig
