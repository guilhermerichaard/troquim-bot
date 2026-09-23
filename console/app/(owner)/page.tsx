import Link from 'next/link'
import { redirect } from 'next/navigation'
import { troquimFetch } from '@/lib/troquim'

type Overview = {
  businessName: string
  whatsappStatus: string
  activeAppointments: number
  activeCustomers: number
  activeServices: number
  activeProfessionals: number
}

type Appointment = {
  id: string
  date: string
  startTime: string
  endTime: string
  status: string
  customerName: string
  serviceName: string
  professionalName: string
}

export default async function TodayPage() {
  const [overview, appointments] = await Promise.all([
    troquimFetch<Overview>('/overview'),
    troquimFetch<Appointment[]>('/appointments'),
  ])
  if (!overview) redirect('/login')
  const next=(appointments ?? []).slice(0,6)

  return <div className="page">
    <div className="eyebrow">Hoje no Troquim</div>
    <div className="sectionHead">
      <div><h1 className="title">{overview.businessName}</h1><p className="subtitle">A operação que o Troquim está cuidando por você.</p></div>
      <Link className="touchButton primary desktopOnly" href="/novo">+ Novo agendamento</Link>
    </div>

    <div className="grid">
      <div className="card"><div className="metric">{overview.activeAppointments}</div><div className="label">agendamentos ativos</div></div>
      <div className="card"><div className="metric">{overview.activeCustomers}</div><div className="label">clientes ativos</div></div>
      <div className="card"><div className="metric">{overview.activeServices}</div><div className="label">serviços ativos</div></div>
      <div className="card"><div className="metric">{overview.activeProfessionals}</div><div className="label">profissionais ativos</div></div>
    </div>

    <section className="section">
      <div className="sectionHead"><h2>WhatsApp</h2><span className="status">{overview.whatsappStatus}</span></div>
    </section>

    <section className="section">
      <div className="sectionHead"><h2>Próximos</h2><Link href="/agenda" className="label">Ver agenda</Link></div>
      <div className="desktopOnly">{next.length?<table className="table"><thead><tr><th>Data</th><th>Horário</th><th>Cliente</th><th>Serviço</th><th>Profissional</th><th>Status</th></tr></thead><tbody>{next.map(a=><tr key={a.id}><td>{a.date}</td><td>{a.startTime.slice(0,5)}–{a.endTime.slice(0,5)}</td><td>{a.customerName}</td><td>{a.serviceName}</td><td>{a.professionalName}</td><td>{a.status}</td></tr>)}</tbody></table>:<div className="card empty">Nenhum agendamento próximo.</div>}</div>
      <div className="mobileOnly appointmentCards">{next.length?next.map(a=><article className="appointmentCard" key={a.id}><div className="appointmentTop"><div><div className="appointmentTime">{a.startTime.slice(0,5)}</div><strong>{a.customerName}</strong></div><span className="pill">{a.status}</span></div><div className="appointmentMeta">{new Date(a.date+'T12:00:00').toLocaleDateString('pt-BR',{weekday:'short',day:'2-digit',month:'short'})}<br/>{a.serviceName} · {a.professionalName}</div></article>):<div className="card empty">Nenhum agendamento próximo.</div>}</div>
    </section>
  </div>
}
