'use client'

import { useEffect, useMemo, useState } from 'react'

type Role={id:string;roleName:string;quantity:number;filledQuantity:number;payCents:number|null;notes?:string|null}
type Application={id:string;roleId:string;applicantName:string;applicantPhone:string;message?:string|null;status:string}
type EventItem={id:string;title:string;eventType:string;city:string;venue?:string|null;startsAt:string;endsAt:string;budgetCents:number|null;status:string;roles:Role[];applications:Application[]}
type DraftRole={roleName:string;quantity:number;pay:string;notes:string}

const emptyRole=():DraftRole=>({roleName:'',quantity:1,pay:'',notes:''})
const money=(cents:number|null)=>cents==null?'A combinar':(cents/100).toLocaleString('pt-BR',{style:'currency',currency:'BRL'})
const when=(value:string)=>new Date(value).toLocaleString('pt-BR',{dateStyle:'short',timeStyle:'short'})

export default function EventosPage(){
  const [events,setEvents]=useState<EventItem[]>([])
  const [loading,setLoading]=useState(true)
  const [saving,setSaving]=useState(false)
  const [error,setError]=useState('')
  const [showForm,setShowForm]=useState(false)
  const [title,setTitle]=useState('')
  const [eventType,setEventType]=useState('Aniversário')
  const [city,setCity]=useState('São Paulo')
  const [venue,setVenue]=useState('')
  const [startsAt,setStartsAt]=useState('')
  const [endsAt,setEndsAt]=useState('')
  const [budget,setBudget]=useState('')
  const [roles,setRoles]=useState<DraftRole[]>([emptyRole()])

  async function load(){
    setLoading(true);setError('')
    try{
      const r=await fetch('/api/app/events',{cache:'no-store'})
      if(!r.ok)throw new Error('Não foi possível carregar eventos.')
      setEvents(await r.json())
    }catch(e){setError(e instanceof Error?e.message:'Falha ao carregar eventos.')}
    finally{setLoading(false)}
  }

  useEffect(()=>{load();setShowForm(new URLSearchParams(window.location.search).get('novo')==='1')},[])

  const openSlots=useMemo(()=>events.flatMap(e=>e.roles).reduce((s,r)=>s+Math.max(0,r.quantity-r.filledQuantity),0),[events])

  function updateRole(index:number,patch:Partial<DraftRole>){
    setRoles(items=>items.map((r,i)=>i===index?{...r,...patch}:r))
  }

  async function publish(){
    if(saving)return
    setSaving(true);setError('')
    try{
      const payload={
        title,eventType,city,venue:venue||null,startsAt,endsAt,
        budgetCents:budget?Math.round(Number(budget.replace(',','.'))*100):null,
        roles:roles.map(r=>({roleName:r.roleName,quantity:r.quantity,payCents:r.pay?Math.round(Number(r.pay.replace(',','.'))*100):null,notes:r.notes||null}))
      }
      const r=await fetch('/api/app/events',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)})
      const body=await r.json().catch(()=>({}))
      if(!r.ok)throw new Error(body.error||'Não foi possível publicar o evento.')
      setTitle('');setVenue('');setStartsAt('');setEndsAt('');setBudget('');setRoles([emptyRole()]);setShowForm(false)
      await load()
    }catch(e){setError(e instanceof Error?e.message:'Não foi possível publicar o evento.')}
    finally{setSaving(false)}
  }

  const ready=title&&eventType&&city&&startsAt&&endsAt&&roles.length>0&&roles.every(r=>r.roleName&&r.quantity>0)

  return <div className="page">
    <div className="eyebrow">Contratante</div>
    <div className="sectionHead"><div><h1 className="title">Eventos</h1><p className="subtitle">Publique a necessidade e acompanhe o preenchimento da equipe em tempo real.</p></div><button className="touchButton primary" onClick={()=>setShowForm(v=>!v)}>{showForm?'Fechar':'+ Publicar evento'}</button></div>

    <div className="inlineNotice">{openSlots} vaga(s) ainda abertas nos seus eventos.</div>
    {error&&<div className="inlineError" role="alert">{error}</div>}

    {showForm&&<section className="card formGrid">
      <h2>Nova demanda</h2>
      <div className="formField"><label>Título</label><input value={title} onChange={e=>setTitle(e.target.value)} placeholder="Aniversário de 15 anos — Zona Sul"/></div>
      <div className="formField"><label>Tipo</label><select value={eventType} onChange={e=>setEventType(e.target.value)}><option>Aniversário</option><option>Casamento</option><option>Evento corporativo</option><option>Formatura</option><option>Show / entretenimento</option><option>Outro</option></select></div>
      <div className="formField"><label>Cidade</label><input value={city} onChange={e=>setCity(e.target.value)}/></div>
      <div className="formField"><label>Local</label><input value={venue} onChange={e=>setVenue(e.target.value)} placeholder="Buffet, salão, endereço ou a combinar"/></div>
      <div className="formField"><label>Início</label><input type="datetime-local" value={startsAt} onChange={e=>setStartsAt(e.target.value)}/></div>
      <div className="formField"><label>Fim</label><input type="datetime-local" value={endsAt} onChange={e=>setEndsAt(e.target.value)}/></div>
      <div className="formField"><label>Orçamento total estimado (R$)</label><input inputMode="decimal" value={budget} onChange={e=>setBudget(e.target.value)} placeholder="1500"/></div>

      <div className="formField"><label>Equipe / atrações necessárias</label>
        {roles.map((role,index)=><div className="card formGrid" key={index}>
          <input aria-label="Função" value={role.roleName} onChange={e=>updateRole(index,{roleName:e.target.value})} placeholder="DJ, recreador, fotógrafo, garçom..."/>
          <input aria-label="Quantidade" type="number" min={1} value={role.quantity} onChange={e=>updateRole(index,{quantity:Number(e.target.value)})}/>
          <input aria-label="Cachê por pessoa" inputMode="decimal" value={role.pay} onChange={e=>updateRole(index,{pay:e.target.value})} placeholder="Cachê por pessoa (R$)"/>
          <input aria-label="Observações da função" value={role.notes} onChange={e=>updateRole(index,{notes:e.target.value})} placeholder="Experiência, roupa, equipamento..."/>
          {roles.length>1&&<button className="touchButton danger" onClick={()=>setRoles(items=>items.filter((_,i)=>i!==index))}>Remover função</button>}
        </div>)}
        <button className="touchButton secondary" onClick={()=>setRoles(items=>[...items,emptyRole()])}>+ Outra função</button>
      </div>

      <button disabled={!ready||saving} className="touchButton primary" onClick={publish}>{saving?'Publicando…':'Publicar no marketplace'}</button>
    </section>}

    {loading?<div className="card">Carregando eventos…</div>:<div className="appointmentCards">{events.length?events.map(e=><article className="appointmentCard" key={e.id}>
      <div className="appointmentTop"><div><div className="label">{e.eventType} · {e.city}</div><div className="appointmentTime">{when(e.startsAt)}</div><strong>{e.title}</strong></div><span className="pill">{e.status}</span></div>
      <div className="appointmentMeta">{e.venue||'Local a combinar'} · orçamento {money(e.budgetCents)}</div>
      <div className="formGrid" style={{marginTop:12}}>{e.roles.map(r=><div className="inlineNotice" key={r.id}><strong>{r.roleName}</strong> · {r.filledQuantity}/{r.quantity} confirmado(s) · {money(r.payCents)}</div>)}</div>
      <div className="label" style={{marginTop:10}}>{e.applications.length} candidatura(s) recebida(s)</div>
    </article>):<div className="card empty">Nenhum evento publicado ainda.</div>}</div>}
  </div>
}
