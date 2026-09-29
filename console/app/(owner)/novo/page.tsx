'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAvailableSlots } from '@/lib/use-available-slots'
import { createCommandKeys } from '@/lib/booking-request'

type Customer={id:string;name:string;phone:string;status:string}
type Pro={id:string;name:string}
type Catalog={id:string;name:string;description?:string;durationMinutes:number;price:number|null;professionals:Pro[]}

function localIso(d=new Date()){
  return new Intl.DateTimeFormat('en-CA', { timeZone:'America/Sao_Paulo', year:'numeric', month:'2-digit', day:'2-digit' }).format(d)
}

export default function NovoPage(){
  const router=useRouter()
  const [catalog,setCatalog]=useState<Catalog[]>([])
  const [customers,setCustomers]=useState<Customer[]>([])
  const [serviceId,setServiceId]=useState('')
  const [professionalId,setProfessionalId]=useState('')
  const [date,setDate]=useState(localIso())
  const availability=useAvailableSlots(serviceId,professionalId,date)
  const {slots}=availability
  const [selection,setSelection]=useState({query:'',time:''})
  const time=selection.query===availability.query&&slots.includes(selection.time)?selection.time:''
  const setTime=(value:string)=>setSelection({query:availability.query,time:value})
  const commandKey=useRef(createCommandKeys())
  const pending=useRef(false)
  const [customerId,setCustomerId]=useState('')
  const [creatingCustomer,setCreatingCustomer]=useState(false)
  const [customerName,setCustomerName]=useState('')
  const [customerPhone,setCustomerPhone]=useState('')
  const [error,setError]=useState('')
  const [saving,setSaving]=useState(false)

  useEffect(()=>{Promise.all([
    fetch('/api/app/catalog',{cache:'no-store'}).then(r=>{if(!r.ok)throw new Error();return r.json()}),
    fetch('/api/app/customers',{cache:'no-store'}).then(r=>{if(!r.ok)throw new Error();return r.json()}),
  ]).then(([c,u])=>{setCatalog(c);setCustomers(u.filter((x:Customer)=>x.status==='ATIVO'))}).catch(()=>setError('Não foi possível carregar o catálogo.'))},[])

  const service=useMemo(()=>catalog.find(x=>x.id===serviceId),[catalog,serviceId])

  async function createCustomer(){
    if(pending.current)return
    pending.current=true;setSaving(true);setError('')
    try{
      const r=await fetch('/api/app/customers',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:customerName,phone:customerPhone,notes:null})})
      const body=await r.json().catch(()=>({}))
      if(!r.ok) throw new Error(body.error||'Não foi possível criar o cliente.')
      const refreshed=await fetch('/api/app/customers',{cache:'no-store'}).then(x=>x.json())
      setCustomers(refreshed.filter((x:Customer)=>x.status==='ATIVO'))
      setCustomerId(body.id)
      setCreatingCustomer(false)
      setCustomerName('')
      setCustomerPhone('')
    }catch(e){setError(e instanceof Error?e.message:'Não foi possível criar o cliente.')}finally{pending.current=false;setSaving(false)}
  }

  async function submit(){
    if(pending.current)return
    pending.current=true;setSaving(true);setError('')
    try{
      if(!serviceId||!professionalId||!date||!time||!customerId)throw new Error('Escolha os dados e um horário disponível.')
      const command={customerId,serviceId,professionalId,date,time}
      const r=await fetch('/api/app/appointments',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
        ...command,idempotencyKey:commandKey.current(command)
      })})
      const body=await r.json().catch(()=>({}))
      if(!r.ok) throw new Error(body.error||body.message||'Não foi possível agendar.')
      router.push('/agenda');router.refresh()
    }catch(e){setError(e instanceof Error?e.message:'Não foi possível agendar.')}finally{pending.current=false;setSaving(false)}
  }

  const ready=serviceId&&professionalId&&date&&time&&customerId
  return <div className="page">
    <div className="eyebrow">Agendamento manual</div><h1 className="title">Novo horário</h1>
    <p className="subtitle">O mesmo catálogo e a mesma disponibilidade usados no WhatsApp.</p>
    {error&&<div className="inlineError" role="alert">{error}</div>}
    <div className="formGrid">
      <div className="formField"><label>Serviço</label><div className="choiceGrid">{catalog.map(x=><button key={x.id} className={serviceId===x.id?'choiceCard selected':'choiceCard'} disabled={saving} onClick={()=>{setServiceId(x.id);setProfessionalId('');setTime('')}}><strong>{x.name}</strong><div className="label">{x.durationMinutes} min{x.price!=null?` · ${x.price.toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}`:''}</div></button>)}</div></div>
      {service&&<div className="formField"><label>Profissional</label><select disabled={saving} aria-label="Profissional" value={professionalId} onChange={e=>{setProfessionalId(e.target.value);setTime('')}}><option value="">Escolha</option>{service.professionals.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select></div>}
      {professionalId&&<div className="formField"><label>Data</label><input disabled={saving} aria-label="Data" type="date" value={date} min={localIso()} onChange={e=>{setDate(e.target.value);setTime('')}}/></div>}
      {professionalId&&<div className="formField"><label>Horário</label>{availability.loading?<div role="status">Consultando horários…</div>:availability.error?<div role="alert">{availability.error} <button className="touchButton secondary" onClick={availability.retry}>Tentar novamente</button></div>:slots.length?<div className="actionRow">{slots.map(s=><button disabled={saving} key={s} className={time===s?'touchButton primary':'touchButton secondary'} onClick={()=>setTime(s)}>{s.slice(0,5)}</button>)}</div>:<div className="inlineNotice">Sem horários livres nessa data.</div>}</div>}
      {time&&<div className="formField"><label>Cliente</label><select disabled={saving} aria-label="Cliente" value={customerId} onChange={e=>setCustomerId(e.target.value)}><option value="">Escolha o cliente</option>{customers.map(c=><option key={c.id} value={c.id}>{c.name} · {c.phone}</option>)}</select><button className="touchButton secondary" onClick={()=>setCreatingCustomer(v=>!v)}>{creatingCustomer?'Cancelar cadastro':'+ Novo cliente'}</button></div>}
      {time&&creatingCustomer&&<div className="card formGrid"><div className="formField"><label>Nome</label><input value={customerName} onChange={e=>setCustomerName(e.target.value)}/></div><div className="formField"><label>WhatsApp / telefone</label><input inputMode="tel" value={customerPhone} onChange={e=>setCustomerPhone(e.target.value)} placeholder="+55 11 99999-9999"/></div><button className="touchButton primary" disabled={!customerName||!customerPhone||saving} onClick={createCustomer}>{saving?'Salvando…':'Salvar cliente e continuar'}</button></div>}
      <button className="touchButton primary" disabled={!ready||saving||availability.loading} onClick={submit}>{saving?'Agendando…':'Confirmar agendamento'}</button>
    </div>
  </div>
}
