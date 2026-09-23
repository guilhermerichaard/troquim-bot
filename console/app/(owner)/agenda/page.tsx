'use client'

import { useEffect, useState } from 'react'

type Appointment={id:string;date:string;startTime:string;endTime:string;status:string;customerName:string;serviceName:string;professionalName:string}

export default function AgendaPage(){
 const [date,setDate]=useState(new Date().toISOString().slice(0,10))
 const [items,setItems]=useState<Appointment[]>([])
 const [busy,setBusy]=useState('')
 const [error,setError]=useState('')

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

 const days=Array.from({length:7},(_,i)=>{const d=new Date();d.setDate(d.getDate()+i);return d})
 return <div className="page">
   <div className="eyebrow">Operação</div><h1 className="title">Agenda</h1>
   <p className="subtitle">Toque em um dia. Sem grade minúscula de desktop.</p>
   <div className="dayStrip">{days.map(d=>{const iso=d.toISOString().slice(0,10);return <button key={iso} className={date===iso?'dayChip active':'dayChip'} onClick={()=>setDate(iso)}><small>{d.toLocaleDateString('pt-BR',{weekday:'short'})}</small><strong>{d.getDate()}</strong></button>})}</div>
   {error&&<div className="inlineError">{error}</div>}
   <div className="appointmentCards">{items.length?items.map(a=><article className="appointmentCard" key={a.id}>
     <div className="appointmentTop"><div><div className="appointmentTime">{a.startTime.slice(0,5)}</div><strong>{a.customerName}</strong></div><span className="pill">{a.status}</span></div>
     <div className="appointmentMeta">{a.serviceName}<br/>{a.professionalName} · até {a.endTime.slice(0,5)}</div>
     <div className="actionRow" style={{marginTop:12}}><button className="touchButton danger" disabled={busy===a.id} onClick={()=>cancel(a.id)}>{busy===a.id?'Cancelando…':'Cancelar'}</button></div>
   </article>):<div className="card empty">Nenhum agendamento neste dia.</div>}</div>
 </div>
}
