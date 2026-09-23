'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { CalendarDays, Home, Scissors, Users, UserRound, LogOut } from 'lucide-react'

const items = [
  { href: '/', label: 'Hoje', icon: Home },
  { href: '/agenda', label: 'Agenda', icon: CalendarDays },
  { href: '/clientes', label: 'Clientes', icon: Users },
  { href: '/servicos', label: 'Serviços', icon: Scissors },
  { href: '/equipe', label: 'Equipe', icon: UserRound },
]

export function Sidebar() {
  const router = useRouter()
  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' })
    router.push('/login')
    router.refresh()
  }

  return <aside className="sidebar">
    <div className="brand">Troquim<span>.</span></div>
    <nav className="nav">
      {items.map(({ href, label, icon: Icon }) => (
        <Link href={href} key={href}><Icon size={17}/><span>{label}</span></Link>
      ))}
    </nav>
    <div style={{marginTop:'auto'}} className="nav">
      <button onClick={logout} style={{border:0,background:'transparent',cursor:'pointer'}}><LogOut size={17}/><span>Sair</span></button>
    </div>
  </aside>
}
