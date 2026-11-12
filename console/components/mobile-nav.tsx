'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { CalendarDays, Home, Plus, Search, Users } from 'lucide-react'

const items = [
  { href: '/', label: 'Hoje', icon: Home },
  { href: '/eventos', label: 'Eventos', icon: CalendarDays },
  { href: '/eventos?novo=1', label: 'Publicar', icon: Plus, primary: true },
  { href: '/oportunidades', label: 'Vagas', icon: Search },
  { href: '/talentos', label: 'Talentos', icon: Users },
]

export function MobileNav() {
  const pathname = usePathname()
  return <nav className="mobileNav">
    {items.map(({href,label,icon:Icon,primary}) => {
      const baseHref = href.split('?')[0]
      const active = baseHref === '/' ? pathname === '/' : pathname.startsWith(baseHref)
      return <Link key={href} href={href} className={primary ? 'mobileNavPrimary' : active ? 'mobileNavItem active' : 'mobileNavItem'}>
        <span className={primary ? 'mobileNavPrimaryIcon' : ''}><Icon size={primary ? 24 : 21}/></span>
        <small>{label}</small>
      </Link>
    })}
  </nav>
}
