import { redirect } from 'next/navigation'
import { Sidebar } from '@/components/sidebar'
import { MobileNav } from '@/components/mobile-nav'
import { troquimFetch } from '@/lib/troquim'

type Overview = { businessName: string }

export default async function OwnerLayout({ children }: { children: React.ReactNode }) {
  const overview = await troquimFetch<Overview>('/overview')
  if (!overview) redirect('/login')

  return <div className="shell">
    <Sidebar />
    <main className="main">{children}</main>
    <MobileNav />
  </div>
}
