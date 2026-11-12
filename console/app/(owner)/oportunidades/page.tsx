import { ownerBackendUrl } from '@/lib/troquim'

type Role={id:string;roleName:string;quantity:number;filledQuantity:number;payCents:number|null;notes?:string|null}
type Opportunity={id:string;title:string;eventType:string;city:string;venue?:string|null;startsAt:string;endsAt:string;budgetCents:number|null;status:string;roles:Role[]}

const money=(cents:number|null)=>cents==null?'A combinar':(cents/100).toLocaleString('pt-BR',{style:'currency',currency:'BRL'})

export default async function OportunidadesPage(){
  const response=await fetch(`${ownerBackendUrl()}/api/v1/marketplace/opportunities`,{cache:'no-store'})
  const items:Opportunity[]=response.ok?await response.json():[]

  return <div className="page">
    <div className="eyebrow">Marketplace</div><h1 className="title">Oportunidades</h1>
    <p className="subtitle">O que um freelancer vê: eventos abertos, funções disponíveis e cachê informado.</p>
    <div className="appointmentCards">{items.length?items.map(e=><article className="appointmentCard" key={e.id}>
      <div className="appointmentTop"><div><div className="label">{e.eventType} · {e.city}</div><strong>{e.title}</strong><div>{new Date(e.startsAt).toLocaleString('pt-BR')}</div></div><span className="pill">{e.status}</span></div>
      <div className="appointmentMeta">{e.venue||'Local a combinar'} · orçamento {money(e.budgetCents)}</div>
      <div className="formGrid" style={{marginTop:12}}>{e.roles.filter(r=>r.filledQuantity<r.quantity).map(r=><div className="inlineNotice" key={r.id}><strong>{r.roleName}</strong> · {r.quantity-r.filledQuantity} vaga(s) · {money(r.payCents)}{r.notes?` · ${r.notes}`:''}</div>)}</div>
    </article>):<div className="card empty">Nenhuma oportunidade aberta neste momento.</div>}</div>
  </div>
}
