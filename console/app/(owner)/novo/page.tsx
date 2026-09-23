'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'

type Customer={id:string;name:string;phone:string;status:string}
type Pro={id:string;name:string}
type Catalog={id:string;name:string;description?:string;durationMinutes:number;price:number|null;professionals:Pro[]}

function localIso(d=new Date()){
  const y=d.getFullYear()
  const m=String(d.getMonth()+1).padStart(2,'0')
  const day=String(d.getDate()).padStart(2,'0')
  return `${y}-${m}-${day}`
}

export default function NovoPage(){
  const router=useRouter()
  const [catalog,setCatalog]=useState<Catalog[]>([])
  const [customers,setCustomers]=useState<Customer[]>([])
  const [serviceId,setServiceId]=useState('')
  const [professionalId,setProfessionalId]=useState('')
  const [date,setDate]=useState(localIso())
  const [slots,setSlots]=useState<string[]>([])
  const [time,setTime]=useState('')
  const [customerId,setCustomerId]=useState('')
  const [error,setError]=useState('')
  const [saving,setSaving]=useState(false)

  useEffect(()=>{Promise.all([
    fetch('/api/app/catalog').then(r=>r.json()),
    fetch('/api/app/customers').then(r=>r.json()),
  ]).then(([c,u])=>{setCatalog(c);setCustomers(u.filter((x:Customer)=>x.status==='ATIVO'))}).catch(()=>setError('Não foi possível carregar o catálogo.'))},[])

  const service=useMemo(()=>catalog.find(x=>x.id===serviceId),[catalog,serviceId])
  useEffect(()=>{setProfessionalId('');setSlots([]);setTime('')},[serviceId])
  useEffect(()=>{
    if(!serviceId||!professionalId||!date)return
    fetch(`/api/app/slots?serviceId=${encodeURIComponent(serviceId)}&professionalId=${encodeURIComponent(professionalId)}&date=${date}`)
      .then(async r=>{if(!r.ok) throw new Error(); return r.json()})
      .then(setSlots).catch(()=>setSlots([]))
  },[serviceId,professionalId,date])

  async function submit(){
    setSaving(true);setError('')
    try{
      const r=await fetch('/api/app/appointments',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
        customerId,serviceId,professionalId,date,time,idempotencyKey:crypto.randomUUID()
      })})
      const body=await r.json().catch(()=>({}))
      if(!r.ok) throw new Error(body.error||body.message||'Não foi possível agendar.')
      router.push('/agenda');router.refresh()
    }catch(e){setError(e instanceof Error?e.message:'Não foi possível agendar.')}finally{setSaving(false)}
  }

  const ready=serviceId&&professionalId&&date&&time&&customerId
  return <div className="page">
    <div className="eyebrow">Agendamento manual</div><h1 className="title">Novo horário</h1>
    <p className="subtitle">O mesmo catálogo e a mesma disponibilidade usados no WhatsApp.</p>
    {error&&<div className="inlineError">{error}</div>}
    <div className="formGrid">
      <div className="formField"><label>Serviço</label><div className="choiceGrid">{catalog.map(x=><button key={x.id} className={serviceId===x.id?'choiceCard selected':'choiceCard'} onClick={()=>setServiceId(x.id)}><strong>{x.name}</strong><div className="label">{x.durationMinutes} min{x.price!=null?` · ${x.price.toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}`:''}</div></button>)}</div></div>
      {service&&<div className="formField"><label>Profissional</label><select value={professionalId} onChange={e=>setProfessionalId(e.target.value)}><option value="">Escolha</option>{service.professionals.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select></div>}
      {professionalId&&<div className="formField"><label>Data</label><input type="date" value={date} min={localIso()} onChange={e=>setDate(e.target.value)}/></div>}
      {professionalId&&<div className="formField"><label>Horário</label>{slots.length?<div className="actionRow">{slots.map(s=><button key={s} className={time===s?'touchButton primary':'touchButton secondary'} onClick={()=>setTime(s)}>{s.slice(0,5)}</button>)}</div>:<div className="inlineNotice">Sem horários livres nessa data.</div>}</div>}
      {time&&<div className="formField"><label>Cliente</label><select value={customerId} onChange={e=>setCustomerId(e.target.value)}><option value="">Escolha o cliente</option>{customers.map(c=><option key={c.id} value={c.id}>{c.name} · {c.phone}</option>)}</select></div>}
      <button className="touchButton primary" disabled={!ready||saving} onClick={submit}>{saving?'Agendando…':'Confirmar agendamento'}</button>
    </div>
  </div>
}
