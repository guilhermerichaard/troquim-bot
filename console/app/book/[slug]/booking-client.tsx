'use client'

import { useMemo, useState } from 'react'

export type Professional={id:string;name:string}
export type Service={id:string;name:string;description?:string|null;durationMinutes:number;price:number|null;professionals:Professional[]}
export type PublicBusiness={
  slug:string;name:string;shortDescription?:string|null;publicPhone?:string|null;
  publicAddress?:string|null;catalogConfigured:boolean;services:Service[]
}

type Availability={date:string;condition:string;times:string[]}

function localIso(d=new Date()){
  const y=d.getFullYear()
  const m=String(d.getMonth()+1).padStart(2,'0')
  const day=String(d.getDate()).padStart(2,'0')
  return `${y}-${m}-${day}`
}

export function BookingClient({business}:{business:PublicBusiness}){
  const [serviceId,setServiceId]=useState('')
  const [professionalId,setProfessionalId]=useState('')
  const [date,setDate]=useState(localIso())
  const [times,setTimes]=useState<string[]>([])
  const [time,setTime]=useState('')
  const [name,setName]=useState('')
  const [phone,setPhone]=useState('')
  const [loading,setLoading]=useState(false)
  const [message,setMessage]=useState('')
  const [done,setDone]=useState(false)

  const service=useMemo(()=>business.services.find(x=>x.id===serviceId),[business.services,serviceId])

  async function loadTimes(nextService=serviceId,nextProfessional=professionalId,nextDate=date){
    setMessage('')
    setTime('')
    if(!nextService||!nextProfessional||!nextDate){setTimes([]);return}
    setLoading(true)
    try{
      const query=new URLSearchParams({serviceId:nextService,professionalId:nextProfessional,date:nextDate})
      const r=await fetch(`/api/public/businesses/${encodeURIComponent(business.slug)}/availability?${query}`,{cache:'no-store'})
      const body=await r.json().catch(()=>null) as Availability|null
      setTimes(r.ok&&body?body.times:[])
      if(!r.ok)setMessage('Não consegui consultar a agenda agora.')
      else if(!body?.times?.length)setMessage('Sem horários livres nessa data. Tente outro dia.')
    }finally{setLoading(false)}
  }

  async function submit(){
    if(!serviceId||!professionalId||!date||!time||!name.trim()||!phone.trim())return
    setLoading(true);setMessage('')
    const key=crypto.randomUUID()
    try{
      const r=await fetch(`/api/public/businesses/${encodeURIComponent(business.slug)}/appointments`,{
        method:'POST',
        headers:{'Content-Type':'application/json','Idempotency-Key':key},
        body:JSON.stringify({
          serviceId,professionalId,date,time,
          customerName:name.trim(),customerPhone:phone.trim()
        })
      })
      const body=await r.json().catch(()=>({})) as {message?:string;code?:string}
      if(r.ok){setDone(true);return}
      if(r.status===409&&body.code==='SLOT_UNAVAILABLE'){
        setMessage('Esse horário acabou de ficar indisponível. Atualizei os horários livres para você.')
        await loadTimes()
        return
      }
      setMessage(body.message||'Não foi possível concluir o agendamento.')
    }finally{setLoading(false)}
  }

  if(done){
    return <main className="publicBookingShell"><section className="publicBookingCard publicBookingSuccess">
      <div className="publicBookingCheck">✓</div>
      <div className="eyebrow">Tudo certo</div>
      <h1>Agendamento confirmado</h1>
      <p>{service?.name} · {date.split('-').reverse().join('/')} às {time.slice(0,5)}</p>
      <p className="label">Você pode fechar esta página. O negócio receberá o agendamento na mesma agenda usada pelo WhatsApp.</p>
    </section></main>
  }

  return <main className="publicBookingShell">
    <section className="publicBookingCard">
      <div className="eyebrow">Agendamento online</div>
      <h1>{business.name}</h1>
      {business.shortDescription&&<p className="subtitle">{business.shortDescription}</p>}
      {business.publicAddress&&<div className="publicBusinessMeta">{business.publicAddress}</div>}

      {!business.catalogConfigured?<div className="inlineNotice">A agenda online ainda não foi configurada.</div>:
      <div className="publicBookingSteps">
        <div className="formField">
          <label>1. Escolha o serviço</label>
          <div className="choiceGrid">{business.services.map(s=><button key={s.id}
            className={serviceId===s.id?'choiceCard selected':'choiceCard'}
            onClick={()=>{setServiceId(s.id);setProfessionalId('');setTimes([]);setTime('')}}>
            <strong>{s.name}</strong>
            <span className="label">{s.durationMinutes} min{s.price!=null?` · ${s.price.toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}`:''}</span>
          </button>)}</div>
        </div>

        {service&&<div className="formField">
          <label>2. Profissional</label>
          <div className="choiceGrid">{service.professionals.map(p=><button key={p.id}
            className={professionalId===p.id?'choiceCard selected':'choiceCard'}
            onClick={()=>{setProfessionalId(p.id);void loadTimes(service.id,p.id,date)}}>{p.name}</button>)}</div>
        </div>}

        {professionalId&&<div className="formField">
          <label>3. Data</label>
          <input type="date" min={localIso()} value={date}
            onChange={e=>{setDate(e.target.value);void loadTimes(serviceId,professionalId,e.target.value)}}/>
        </div>}

        {professionalId&&<div className="formField">
          <label>4. Horário</label>
          {loading&&!times.length?<div className="inlineNotice">Consultando horários…</div>:
          times.length?<div className="actionRow">{times.map(t=><button key={t}
            className={time===t?'touchButton primary':'touchButton secondary'}
            onClick={()=>setTime(t)}>{t.slice(0,5)}</button>)}</div>:
          <div className="inlineNotice">Escolha outra data para ver horários livres.</div>}
        </div>}

        {time&&<div className="publicCustomerFields">
          <div className="formField"><label>Seu nome</label><input value={name} onChange={e=>setName(e.target.value)} autoComplete="name"/></div>
          <div className="formField"><label>WhatsApp / telefone</label><input inputMode="tel" value={phone} onChange={e=>setPhone(e.target.value)} autoComplete="tel" placeholder="+55 11 99999-9999"/></div>
        </div>}

        {message&&<div className="inlineNotice">{message}</div>}
        <button className="touchButton primary publicBookingSubmit"
          disabled={!serviceId||!professionalId||!date||!time||!name.trim()||!phone.trim()||loading}
          onClick={submit}>{loading?'Confirmando…':'Confirmar agendamento'}</button>
      </div>}
    </section>
  </main>
}
