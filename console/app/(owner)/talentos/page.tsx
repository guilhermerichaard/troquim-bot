'use client'

import { useEffect, useState } from 'react'

type Role={id:string;roleName:string;quantity:number;filledQuantity:number;payCents:number|null}
type Application={id:string;roleId:string;applicantName:string;applicantPhone:string;message?:string|null;status:string}
type EventItem={id:string;title:string;city:string;startsAt:string;status:string;roles:Role[];applications:Application[]}

export default function TalentosPage(){
  const [events,setEvents]=useState<EventItem[]>([])
  const [loading,setLoading]=useState(true)
  const [busy,setBusy]=useState('')
  const [error,setError]=useState('')

  async function load(){
    setLoading(true)
    try{
      const r=await fetch('/api/app/events',{cache:'no-store'})
      if(!r.ok)throw new Error()
      setEvents(await r.json())
    }catch{setError('Não foi possível carregar candidaturas.')}
    finally{setLoading(false)}
  }
  useEffect(()=>{load()},[])

  async function accept(eventId:string,applicationId:string){
    if(busy)return
    setBusy(applicationId);setError('')
    try{
      const r=await fetch(`/api/app/events/${eventId}/applications/${applicationId}/accept`,{method:'POST'})
      const body=await r.json().catch(()=>({}))
      if(!r.ok)throw new Error(body.error||'Não foi possível aceitar.')
      await load()
    }catch(e){setError(e instanceof Error?e.message:'Não foi possível aceitar.')}
    finally{setBusy('')}
  }

  const candidates=events.flatMap(event=>event.applications.map(application=>({
    event,application,role:event.roles.find(r=>r.id===application.roleId)
  }))).sort((a,b)=>a.application.status.localeCompare(b.application.status))

  return <div className="page">
    <div className="eyebrow">Escala</div><h1 className="title">Talentos</h1>
    <p className="subtitle">Candidaturas reais recebidas nos seus eventos. O aceite é a decisão oficial de escala.</p>
    {error&&<div className="inlineError" role="alert">{error}</div>}
    {loading?<div className="card">Carregando candidatos…</div>:<div className="appointmentCards">{candidates.length?candidates.map(({event,application,role})=><article className="appointmentCard" key={application.id}>
      <div className="appointmentTop"><div><div className="label">{event.title} · {role?.roleName||'Função'}</div><strong>{application.applicantName}</strong><div>{application.applicantPhone}</div></div><span className="pill">{application.status}</span></div>
      {application.message&&<div className="appointmentMeta">{application.message}</div>}
      {application.status==='APPLIED'&&<button disabled={!!busy} className="touchButton primary" onClick={()=>accept(event.id,application.id)}>{busy===application.id?'Confirmando…':'Aceitar para a escala'}</button>}
    </article>):<div className="card empty">Nenhuma candidatura recebida ainda. Publique um evento para começar.</div>}</div>}
  </div>
}
