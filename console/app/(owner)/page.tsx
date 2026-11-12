import Link from 'next/link'
import { redirect } from 'next/navigation'
import { CalendarDays, Search, Users, CheckCircle2, Plus } from 'lucide-react'
import { troquimFetch } from '@/lib/troquim'

type Role = { id:string; roleName:string; quantity:number; filledQuantity:number; payCents:number|null; notes?:string|null }
type Application = { id:string; roleId:string; applicantName:string; applicantPhone:string; message?:string|null; status:string }
type EventItem = {
  id:string; title:string; eventType:string; city:string; venue?:string|null;
  startsAt:string; endsAt:string; budgetCents:number|null; status:string;
  roles:Role[]; applications:Application[]
}

function money(cents:number|null){
  return cents == null ? 'A combinar' : (cents/100).toLocaleString('pt-BR',{style:'currency',currency:'BRL'})
}

function dateLabel(value:string){
  return new Date(value).toLocaleString('pt-BR',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'})
}

export default async function TodayPage() {
  const events = await troquimFetch<EventItem[]>('/events')
  if (!events) redirect('/login')

  const open = events.filter(e=>['OPEN','MATCHING'].includes(e.status))
  const applications = events.flatMap(e=>e.applications)
  const accepted = applications.filter(a=>a.status==='ACCEPTED').length
  const openSlots = events.flatMap(e=>e.roles).reduce((sum,r)=>sum+Math.max(0,r.quantity-r.filledQuantity),0)
  const next = [...events].filter(e=>new Date(e.endsAt).getTime()>Date.now())
    .sort((a,b)=>new Date(a.startsAt).getTime()-new Date(b.startsAt).getTime()).slice(0,5)

  return <div className="page dashboardPage">
    <header className="dashboardHero">
      <div>
        <div className="eyebrow">Operação de eventos</div>
        <h1 className="dashboardTitle">Troquim Eventos</h1>
        <p className="dashboardSubtitle">Publique a demanda, receba candidatos e feche a escala sem planilha e sem caça no WhatsApp.</p>
      </div>
      <Link className="touchButton primary dashboardPrimaryAction" href="/eventos?novo=1"><Plus size={18}/> Publicar evento</Link>
    </header>

    <section className="dashboardMetrics" aria-label="Resumo do marketplace">
      <article className="metricCard metricCardPrimary"><div className="metricIcon"><CalendarDays size={20}/></div><div><strong>{open.length}</strong><span>eventos buscando equipe</span></div></article>
      <article className="metricCard"><div className="metricIcon"><Search size={20}/></div><div><strong>{openSlots}</strong><span>vagas ainda abertas</span></div></article>
      <article className="metricCard"><div className="metricIcon"><Users size={20}/></div><div><strong>{applications.length}</strong><span>candidaturas recebidas</span></div></article>
      <article className="metricCard"><div className="metricIcon"><CheckCircle2 size={20}/></div><div><strong>{accepted}</strong><span>profissionais confirmados</span></div></article>
    </section>

    <div className="dashboardColumns">
      <section className="dashboardPanel">
        <div className="dashboardPanelHead"><div><span className="panelKicker">Próximos</span><h2>Eventos em operação</h2></div><Link href="/eventos" className="textAction">Ver todos</Link></div>
        {next.length ? <div className="dashboardAppointments">{next.map((e,index)=><article className={index===0?'dashboardAppointment dashboardAppointmentNext':'dashboardAppointment'} key={e.id}>
          <div className="appointmentDateBlock"><strong>{dateLabel(e.startsAt)}</strong><span>{e.city}</span></div>
          <div className="appointmentBody"><div className="appointmentIdentity"><strong>{e.title}</strong><span>{e.eventType} · {e.roles.length} função(ões)</span></div><div className="appointmentProfessional">{money(e.budgetCents)}</div></div>
          <span className="statusBadge">{e.status}</span>
        </article>)}</div> : <div className="dashboardEmpty"><CalendarDays size={24}/><strong>Nenhum evento publicado</strong><span>Crie a primeira demanda para começar a formar a rede.</span></div>}
      </section>

      <aside className="dashboardSide">
        <section className="dashboardPanel quickActionsPanel">
          <div className="dashboardPanelHead"><div><span className="panelKicker">Atalhos</span><h2>Operar agora</h2></div></div>
          <Link href="/eventos?novo=1" className="quickAction"><Plus size={18}/><div><strong>Publicar demanda</strong><span>Data, local, funções e cachês</span></div></Link>
          <Link href="/talentos" className="quickAction"><Users size={18}/><div><strong>Ver candidatos</strong><span>Aceitar profissionais e fechar vagas</span></div></Link>
          <Link href="/oportunidades" className="quickAction"><Search size={18}/><div><strong>Ver marketplace</strong><span>Enxergar o que está aberto para freelancers</span></div></Link>
        </section>
      </aside>
    </div>
  </div>
}
