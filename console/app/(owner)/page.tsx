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

  return <div className="page">
    <div className="eyebrow">Centro de operação</div>
    <h1 className="title">{overview.businessName}</h1>
    <p className="subtitle">O que está acontecendo no negócio agora, sem planilha paralela.</p>

    <div className="grid">
      <div className="card"><div className="metric">{overview.activeAppointments}</div><div className="label">agendamentos ativos</div></div>
      <div className="card"><div className="metric">{overview.activeCustomers}</div><div className="label">clientes ativos</div></div>
      <div className="card"><div className="metric">{overview.activeServices}</div><div className="label">serviços ativos</div></div>
      <div className="card"><div className="metric">{overview.activeProfessionals}</div><div className="label">profissionais ativos</div></div>
    </div>

    <section className="section">
      <div className="sectionHead"><h2>Canal</h2><span className="status">WhatsApp · {overview.whatsappStatus}</span></div>
    </section>

    <section className="section">
      <div className="sectionHead"><h2>Próximos agendamentos</h2></div>
      <AppointmentTable items={(appointments ?? []).slice(0, 8)} />
    </section>
  </div>
}

function AppointmentTable({items}:{items:Appointment[]}) {
  if (!items.length) return <div className="card empty">Nenhum agendamento próximo.</div>
  return <table className="table"><thead><tr><th>Data</th><th>Horário</th><th>Cliente</th><th>Serviço</th><th>Profissional</th><th>Status</th></tr></thead>
    <tbody>{items.map(a => <tr key={a.id}><td>{a.date}</td><td>{a.startTime.slice(0,5)}–{a.endTime.slice(0,5)}</td><td>{a.customerName}</td><td>{a.serviceName}</td><td>{a.professionalName}</td><td>{a.status}</td></tr>)}</tbody>
  </table>
}
