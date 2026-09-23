'use client'

import { useEffect, useState } from 'react'

type Appointment={
  id:string;date:string;startTime:string;endTime:string;status:string;
  customerId:string;serviceId:string;professionalId:string;
  customerName:string;serviceName:string;professionalName:string
}

function localIso(d=new Date()){
  const y=d.getFullYear()
  const m=String(d.getMonth()+1).padStart(2,'0')
  const day=String(d.getDate()).padStart(2,'0')
  return `${y}-${m}-${day}`
}

export default function AgendaPage(){
 const [date,setDate]=useState(localIso())
 const [items,setItems]=useState<Appointment[]>([])
 const [busy,setBusy]=useState('')
 const [error,setError]=useState('')
 const [rescheduling,setRescheduling]=useState<Appointment|null>(null)

 async function load(d=date){
   setError('')
   const r=await fetch('/api/app/appointments?date='+d,{cache:'no-store'})
   if(!r.ok){setError('Não foi possível carregar a agenda.');return}
   setItems(await r.json())
 }
 useEffect(()=>{void load(date)},[date])

 async function cancel(id:string){
   if(!confirm('Cancelar este agendamento?'))return
   setBusy(id);setError('')
   const r=await fetch(`/api/app/appointments/${id}/cancel`,{method:'POST'})
   const body=await r.json().catch(()=>({}))
   if(!r.ok)setError(body.error||'Não foi possível cancelar.')
   await load();setBusy('')
 }

 const days=Array.from({length:10},(_,i)=>{const d=new Date();d.setDate(d.getDate()+i);return d})
 return <div className="page">
   <div className="eyebrow">Operação</div><h1 className="title">Agenda</h1>
   <p className="subtitle">Toque em um dia. No celular, a agenda é uma linha do tempo — não uma planilha apertada.</p>
   <div className="dayStrip">{days.map(d=>{const iso=localIso(d);return <button key={iso} className={date===iso?'dayChip active':'dayChip'} onClick={()=>setDate(iso)}><small>{d.toLocaleDateString('pt-BR',{weekday:'short'})}</small><strong>{d.getDate()}</strong></button>})}</div>
   {error&&<div className="inlineError">{error}</div>}
   <div className="appointmentCards">{items.length?items.map(a=><article className="appointmentCard" key={a.id}>
     <div className="appointmentTop"><div><div className="appointmentTime">{a.startTime.slice(0,5)}</div><strong>{a.customerName}</strong></div><span className="pill">{a.status}</span></div>
     <div className="appointmentMeta">{a.serviceName}<br/>{a.professionalName} · até {a.endTime.slice(0,5)}</div>
     <div className="actionRow" style={{marginTop:12}}>
       <button className="touchButton secondary" onClick={()=>setRescheduling(a)}>Reagendar</button>
       <button className="touchButton danger" disabled={busy===a.id} onClick={()=>cancel(a.id)}>{busy===a.id?'Cancelando…':'Cancelar'}</button>
     </div>
   </article>):<div className="card empty">Nenhum agendamento neste dia.</div>}</div>
   {rescheduling&&<RescheduleSheet item={rescheduling} onClose={()=>setRescheduling(null)} onSaved={async()=>{setRescheduling(null);await load()}}/>}
 </div>
}

function RescheduleSheet({item,onClose,onSaved}:{item:Appointment;onClose:()=>void;onSaved:()=>void}){
 const [date,setDate]=useState(item.date)
 const [slots,setSlots]=useState<string[]>([])
 const [time,setTime]=useState('')
 const [error,setError]=useState('')
 const [saving,setSaving]=useState(false)

 useEffect(()=>{
   setTime('')
   fetch(`/api/app/slots?serviceId=${encodeURIComponent(item.serviceId)}&professionalId=${encodeURIComponent(item.professionalId)}&date=${date}`)
     .then(async r=>{if(!r.ok)throw new Error();return r.json()})
     .then(setSlots)
     .catch(()=>{setSlots([]);setError('Não foi possível consultar horários.')})
 },[date,item.serviceId,item.professionalId])

 async function save(){
   setSaving(true);setError('')
   const r=await fetch(`/api/app/appointments/${item.id}/reschedule`,{
     method:'POST',headers:{'Content-Type':'application/json'},
     body:JSON.stringify({date,time,idempotencyKey:crypto.randomUUID()})
   })
   const body=await r.json().catch(()=>({}))
   if(!r.ok){setError(body.error||'Não foi possível reagendar. O horário original foi preservado.');setSaving(false);return}
   await onSaved()
 }

 return <div className="sheet" onMouseDown={e=>{if(e.currentTarget===e.target)onClose()}}>
   <div className="sheetPanel"><div className="sheetHandle"/>
     <div className="eyebrow">Reagendar</div><h2>{item.customerName}</h2>
     <p className="subtitle">{item.serviceName} · {item.professionalName}</p>
     {error&&<div className="inlineError">{error}</div>}
     <div className="formGrid">
       <div className="formField"><label>Nova data</label><input type="date" min={localIso()} value={date} onChange={e=>setDate(e.target.value)}/></div>
       <div className="formField"><label>Novo horário</label>
         {slots.length?<div className="actionRow">{slots.map(s=><button key={s} className={time===s?'touchButton primary':'touchButton secondary'} onClick={()=>setTime(s)}>{s.slice(0,5)}</button>)}</div>:<div className="inlineNotice">Sem horários livres nessa data.</div>}
       </div>
       <button className="touchButton primary" disabled={!time||saving} onClick={save}>{saving?'Reagendando…':'Confirmar novo horário'}</button>
     </div>
   </div>
 </div>
}
