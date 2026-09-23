import type { MetadataRoute } from 'next'

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Troquim',
    short_name: 'Troquim',
    description: 'Centro de operação do seu negócio',
    start_url: '/',
    display: 'standalone',
    background_color: '#f6f8fa',
    theme_color: '#0d1b2e',
    icons: [{ src: '/icon.svg', sizes: 'any', type: 'image/svg+xml' }],
  }
}
