'use client'

import { useEffect, useMemo, useState } from 'react'

type Policy={
  reminderEnabled:boolean
  reminderHoursBefore:number
  cancellationMinHours:number
  upsellEnabled:boolean
}
type Service={id:string;name:string;status:string}
type UpsellRule={baseServiceId:string;baseServiceName:string;addonServiceId:string;addonServiceName:string;active:boolean}

const defaultPolicy:Policy={
  reminderEnabled:true,
  reminderHoursBefore:24,
  cancellationMinHours:0,
  upsellEnabled:false,
}

export default function AutomacoesPage(){
  const [policy,setPolicy]=useState<Policy>(defaultPolicy)
  const [services,setServices]=useState<Service[]>([])
  const [rules,setRules]=useState<UpsellRule[]>([])
  const [baseServiceId,setBaseServiceId]=useState('')
  const [addonServiceId,setAddonServiceId]=useState('')
  const [saving,setSaving]=useState(false)
  const [message,setMessage]=useState('')
  const [error,setError]=useState('')

  async function load(){
    setError('')
    try{
      const [p,s,u]=await Promise.all([
        fetch('/api/app/automation',{cache:'no-store'}),
        fetch('/api/app/services',{cache:'no-store'}),
        fetch('/api/app/upsells',{cache:'no-store'}),
      ])
      if(!p.ok||!s.ok||!u.ok)throw new Error()
      setPolicy(await p.json())
      setServices((await s.json()).filter((x:Service)=>x.status==='ATIVO'))
      setRules(await u.json())
    }catch{
      setError('Não foi possível carregar as automações.')
    }
  }

  useEffect(()=>{void load()},[])

  const addonOptions=useMemo(
    ()=>services.filter(s=>s.id!==baseServiceId),
    [services,baseServiceId]
  )

  async function savePolicy(){
    setSaving(true);setMessage('');setError('')
    const r=await fetch('/api/app/automation',{
      method:'PUT',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify(policy),
    })
    const body=await r.json().catch(()=>({}))
    if(!r.ok)setError(body.error||'Não foi possível salvar.')
    else{setPolicy(body);setMessage('Automações atualizadas.')}
    setSaving(false)
  }

  async function saveRule(){
    if(!baseServiceId||!addonServiceId)return
    setSaving(true);setMessage('');setError('')
    const r=await fetch(`/api/app/upsells/${baseServiceId}`,{
      method:'PUT',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({addonServiceId,active:true}),
    })
    const body=await r.json().catch(()=>({}))
    if(!r.ok)setError(body.error||'Não foi possível salvar a oferta.')
    else{
      setMessage('Oferta adicional configurada.')
      setBaseServiceId('');setAddonServiceId('')
      await load()
    }
    setSaving(false)
  }

  async function removeRule(baseId:string){
    setSaving(true);setMessage('');setError('')
    const r=await fetch(`/api/app/upsells/${baseId}`,{method:'DELETE'})
    if(!r.ok)setError('Não foi possível remover a oferta.')
    else{setMessage('Oferta removida.');await load()}
    setSaving(false)
  }

  return <div className="page">
    <div className="eyebrow">Automação</div>
    <h1 className="title">Lembretes e vendas</h1>
    <p className="subtitle">Defina a política do negócio uma vez. WhatsApp e web usam as mesmas regras.</p>

    {error&&<div className="inlineError">{error}</div>}
    {message&&<div className="inlineNotice">{message}</div>}

    <div className="settingsGrid">
      <section className="settingCard">
        <div className="settingRow">
          <div><strong>Lembrete automático</strong><div className="settingDescription">Envia um template utilitário antes do horário, sem depender de alguém lembrar manualmente.</div></div>
          <input type="checkbox" checked={policy.reminderEnabled}
            onChange={e=>setPolicy(v=>({...v,reminderEnabled:e.target.checked}))}/>
        </div>
        <div className="formField">
          <label>Quantas horas antes</label>
          <input className="compactInput" type="number" min="1" max="168"
            value={policy.reminderHoursBefore}
            onChange={e=>setPolicy(v=>({...v,reminderHoursBefore:Number(e.target.value)}))}/>
        </div>
      </section>

      <section className="settingCard">
        <div><strong>Política de cancelamento</strong>
          <div className="settingDescription">O cliente pode cancelar sozinho até este limite. O dono continua podendo intervir pelo painel.</div>
        </div>
        <div className="formField">
          <label>Antecedência mínima em horas</label>
          <input className="compactInput" type="number" min="0" max="720"
            value={policy.cancellationMinHours}
            onChange={e=>setPolicy(v=>({...v,cancellationMinHours:Number(e.target.value)}))}/>
        </div>
      </section>

      <section className="settingCard">
        <div className="settingRow">
          <div><strong>Upsell no WhatsApp</strong><div className="settingDescription">Só oferece adicional configurado e somente quando ele realmente cabe na agenda do mesmo profissional.</div></div>
          <input type="checkbox" checked={policy.upsellEnabled}
            onChange={e=>setPolicy(v=>({...v,upsellEnabled:e.target.checked}))}/>
        </div>

        {policy.upsellEnabled&&<>
          <div className="ruleRow">
            <div className="formField"><label>Quando agendar</label>
              <select value={baseServiceId} onChange={e=>{setBaseServiceId(e.target.value);setAddonServiceId('')}}>
                <option value="">Escolha o serviço</option>
                {services.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div className="formField"><label>Oferecer também</label>
              <select value={addonServiceId} onChange={e=>setAddonServiceId(e.target.value)} disabled={!baseServiceId}>
                <option value="">Escolha o adicional</option>
                {addonOptions.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <button className="touchButton secondary" disabled={!baseServiceId||!addonServiceId||saving} onClick={saveRule}>Adicionar oferta</button>
          </div>

          <div className="appointmentCards">
            {rules.map(rule=><article className="appointmentCard" key={rule.baseServiceId}>
              <div className="appointmentTop">
                <div><strong>{rule.baseServiceName}</strong><div className="appointmentMeta">→ {rule.addonServiceName}</div></div>
                <button className="touchButton danger" disabled={saving} onClick={()=>removeRule(rule.baseServiceId)}>Remover</button>
              </div>
            </article>)}
            {!rules.length&&<div className="inlineNotice">Nenhuma oferta adicional configurada ainda.</div>}
          </div>
        </>}
      </section>

      <button className="touchButton primary" disabled={saving} onClick={savePolicy}>
        {saving?'Salvando…':'Salvar automações'}
      </button>
    </div>
  </div>
}
