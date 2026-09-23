import { troquimFetch } from '@/lib/troquim'
type Service={id:string;name:string;description:string|null;durationMinutes:number;price:number|null;status:string}
export default async function ServicosPage(){
 const items=await troquimFetch<Service[]>('/services')??[]
 return <div className="page"><div className="eyebrow">Catálogo</div><h1 className="title">Serviços</h1><p className="subtitle">A mesma fonte usada para interpretar e validar agendamentos.</p>
 {items.length?<table className="table"><thead><tr><th>Serviço</th><th>Duração</th><th>Preço</th><th>Status</th></tr></thead><tbody>{items.map(x=><tr key={x.id}><td><strong>{x.name}</strong><div className="label">{x.description||''}</div></td><td>{x.durationMinutes} min</td><td>{x.price==null?'—':x.price.toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}</td><td>{x.status}</td></tr>)}</tbody></table>:<div className="card empty">Nenhum serviço cadastrado.</div>}</div>
}
