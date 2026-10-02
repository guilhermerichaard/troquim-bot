import Link from 'next/link'
import { redirect } from 'next/navigation'
import { CalendarDays, MessageCircle, Plus, Scissors, Users } from 'lucide-react'
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

function appointmentDate(a: Appointment) {
  return new Date(`${a.date}T${a.startTime}`)
}

function dayLabel(date: string) {
  return new Date(date + 'T12:00:00').toLocaleDateString('pt-BR', {
    weekday: 'short',
    day: '2-digit',
    month: 'short',
  })
}

export default async function TodayPage() {
  const [overview, appointments] = await Promise.all([
    troquimFetch<Overview>('/overview'),
    troquimFetch<Appointment[]>('/appointments'),
  ])
  if (!overview) redirect('/login')

  const next = [...(appointments ?? [])]
    .sort((a,b)=>appointmentDate(a).getTime()-appointmentDate(b).getTime())
    .slice(0,6)

  const first = next[0]

  return <div className="page dashboardPage">
    <header className="dashboardHero">
      <div>
        <div className="eyebrow">Visão operacional</div>
        <h1 className="dashboardTitle">{overview.businessName}</h1>
        <p className="dashboardSubtitle">O que precisa da sua atenção agora, sem ruído.</p>
      </div>
      <Link className="touchButton primary dashboardPrimaryAction" href="/novo">
        <Plus size={18}/> Novo agendamento
      </Link>
    </header>

    <section className="dashboardMetrics" aria-label="Resumo do negócio">
      <article className="metricCard metricCardPrimary">
        <div className="metricIcon"><CalendarDays size={20}/></div>
        <div><strong>{overview.activeAppointments}</strong><span>agendamentos ativos</span></div>
      </article>
      <article className="metricCard">
        <div className="metricIcon"><Users size={20}/></div>
        <div><strong>{overview.activeCustomers}</strong><span>clientes ativos</span></div>
      </article>
      <article className="metricCard">
        <div className="metricIcon"><Scissors size={20}/></div>
        <div><strong>{overview.activeServices}</strong><span>serviços ativos</span></div>
      </article>
      <article className="metricCard">
        <div className="metricIcon"><Users size={20}/></div>
        <div><strong>{overview.activeProfessionals}</strong><span>profissionais ativos</span></div>
      </article>
    </section>

    <div className="dashboardColumns">
      <section className="dashboardPanel">
        <div className="dashboardPanelHead">
          <div>
            <span className="panelKicker">Agenda</span>
            <h2>Próximos atendimentos</h2>
          </div>
          <Link href="/agenda" className="textAction">Abrir agenda</Link>
        </div>

        {next.length ? <div className="dashboardAppointments">
          {next.map((a,index)=><article className={index===0?'dashboardAppointment dashboardAppointmentNext':'dashboardAppointment'} key={a.id}>
            <div className="appointmentDateBlock">
              <strong>{a.startTime.slice(0,5)}</strong>
              <span>{dayLabel(a.date)}</span>
            </div>
            <div className="appointmentBody">
              <div className="appointmentIdentity">
                <strong>{a.customerName}</strong>
                <span>{a.serviceName}</span>
              </div>
              <div className="appointmentProfessional">{a.professionalName}</div>
            </div>
            <span className="statusBadge">{index===0?'Próximo':a.status}</span>
          </article>)}
        </div> : <div className="dashboardEmpty">
          <CalendarDays size={24}/>
          <strong>Agenda livre por enquanto</strong>
          <span>Novos agendamentos aparecerão aqui automaticamente.</span>
        </div>}
      </section>

      <aside className="dashboardSide">
        <section className="dashboardPanel">
          <div className="dashboardPanelHead">
            <div>
              <span className="panelKicker">Canal</span>
              <h2>WhatsApp</h2>
            </div>
            <MessageCircle size={20}/>
          </div>
          <div className="channelStatus">
            <span className="channelDot"/>
            <div><strong>{overview.whatsappStatus}</strong><span>Status informado pelo backend do Troquim.</span></div>
          </div>
        </section>

        <section className="dashboardPanel quickActionsPanel">
          <div className="dashboardPanelHead">
            <div>
              <span className="panelKicker">Atalhos</span>
              <h2>Resolver agora</h2>
            </div>
          </div>
          <Link href="/novo" className="quickAction"><Plus size={18}/><div><strong>Novo agendamento</strong><span>Criar manualmente sem sair do painel</span></div></Link>
          <Link href="/clientes" className="quickAction"><Users size={18}/><div><strong>Clientes</strong><span>Consultar ou cadastrar cliente</span></div></Link>
          <Link href="/automacoes" className="quickAction"><MessageCircle size={18}/><div><strong>Automações</strong><span>Lembretes, cancelamento e upsell</span></div></Link>
        </section>

        {first && <section className="dashboardPanel compactNextPanel">
          <span className="panelKicker">Em seguida</span>
          <strong>{first.customerName}</strong>
          <span>{dayLabel(first.date)} · {first.startTime.slice(0,5)} · {first.serviceName}</span>
        </section>}
      </aside>
    </div>
  </div>
}
