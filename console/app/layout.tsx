import './globals.css'

export const metadata = {
  title: 'Troquim',
  description: 'Centro de operação do Troquim',
  applicationName: 'Troquim',
  appleWebApp: { capable: true, statusBarStyle: 'black-translucent', title: 'Troquim' },
}

export const viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: '#0d1b2e',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html lang="pt-BR"><body>{children}</body></html>
}
