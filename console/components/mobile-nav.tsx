'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { CalendarDays, Home, MoreHorizontal, Plus, Users } from 'lucide-react'

const items = [
  { href: '/', label: 'Hoje', icon: Home },
  { href: '/agenda', label: 'Agenda', icon: CalendarDays },
  { href: '/novo', label: 'Novo', icon: Plus, primary: true },
  { href: '/clientes', label: 'Clientes', icon: Users },
  { href: '/mais', label: 'Mais', icon: MoreHorizontal },
]

export function MobileNav() {
  const pathname = usePathname()
  return <nav className="mobileNav">
    {items.map(({href,label,icon:Icon,primary}) => {
      const active = href === '/' ? pathname === '/' : pathname.startsWith(href)
      return <Link key={href} href={href} className={primary ? 'mobileNavPrimary' : active ? 'mobileNavItem active' : 'mobileNavItem'}>
        <span className={primary ? 'mobileNavPrimaryIcon' : ''}><Icon size={primary ? 24 : 21}/></span>
        <small>{label}</small>
      </Link>
    })}
  </nav>
}
