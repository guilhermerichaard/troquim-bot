'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useRef, useState } from 'react'
import { CalendarDays, Home, Scissors, Users, LogOut, Settings } from 'lucide-react'

const items = [
  { href: '/', label: 'Hoje', icon: Home },
  { href: '/agenda', label: 'Agenda', icon: CalendarDays },
  { href: '/clientes', label: 'Clientes', icon: Users },
  { href: '/servicos', label: 'Serviços', icon: Scissors },
  { href: '/mais', label: 'Mais', icon: Settings },
]

export function Sidebar() {
  const router = useRouter()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  async function logout() {
    if (pending.current) return
    pending.current = true; setBusy(true); setError('')
    try {
      const response = await fetch('/api/auth/logout', { method: 'POST' })
      if (!response.ok) throw new Error()
      router.push('/login'); router.refresh()
    } catch { setError('Não foi possível sair. Tente novamente.') }
    finally { pending.current = false; setBusy(false) }
  }

  return <aside className="sidebar">
    <div className="brand">Troquim<span>.</span></div>
    <nav className="nav">
      {items.map(({ href, label, icon: Icon }) => (
        <Link href={href} key={href}><Icon size={17}/><span>{label}</span></Link>
      ))}
    </nav>
    <div style={{marginTop:'auto'}} className="nav">
      {error && <p role="alert">{error}</p>}
      <button disabled={busy} onClick={logout} style={{border:0,background:'transparent',cursor:'pointer'}}><LogOut size={17}/><span>{busy ? 'Saindo…' : 'Sair'}</span></button>
    </div>
  </aside>
}
