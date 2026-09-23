import { troquimFetch } from '@/lib/troquim'

type Appointment = { id:string; date:string; startTime:string; endTime:string; status:string; customerName:string; serviceName:string; professionalName:string }

export default async function AgendaPage() {
  const items = await troquimFetch<Appointment[]>('/appointments') ?? []
  return <div className="page">
    <div className="eyebrow">Agenda canônica</div><h1 className="title">Agenda</h1>
    <p className="subtitle">Leitura direta do mesmo domínio usado pelo WhatsApp.</p>
    {items.length ? <table className="table"><thead><tr><th>Data</th><th>Horário</th><th>Cliente</th><th>Serviço</th><th>Profissional</th><th>Status</th></tr></thead><tbody>
      {items.map(a=><tr key={a.id}><td>{a.date}</td><td>{a.startTime.slice(0,5)}–{a.endTime.slice(0,5)}</td><td>{a.customerName}</td><td>{a.serviceName}</td><td>{a.professionalName}</td><td>{a.status}</td></tr>)}
    </tbody></table> : <div className="card empty">Nenhum agendamento ativo.</div>}
  </div>
}
