import './globals.css'

export const metadata = {
  title: 'Troquim Console',
  description: 'Centro de operação do Troquim',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html lang="pt-BR"><body>{children}</body></html>
}
