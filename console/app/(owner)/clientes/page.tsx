'use client'

import { useEffect, useState } from 'react'

type Customer={id:string;name:string;phone:string;notes:string|null;status:string}

export default function ClientesPage(){
  const [items,setItems]=useState<Customer[]>([])
  const [creating,setCreating]=useState(false)
  const [error,setError]=useState('')

  async function load(){
    const r=await fetch('/api/app/customers',{cache:'no-store'})
    if(!r.ok){setError('Não foi possível carregar clientes.');return}
    setItems(await r.json())
  }
  useEffect(()=>{void load()},[])

  return <div className="page">
    <div className="sectionHead">
      <div><div className="eyebrow">CRM</div><h1 className="title">Clientes</h1></div>
      <button className="touchButton primary" onClick={()=>setCreating(true)}>+ Novo</button>
    </div>
    <p className="subtitle">Cadastre pelo celular e use imediatamente em um agendamento.</p>
    {error&&<div className="inlineError">{error}</div>}
    <div className="appointmentCards">
      {items.map(x=><article className="appointmentCard" key={x.id}>
        <strong>{x.name}</strong>
        <div className="appointmentMeta">{x.phone}{x.notes?<><br/>{x.notes}</>:null}</div>
        <span className="pill" style={{marginTop:10}}>{x.status}</span>
      </article>)}
      {!items.length&&<div className="card empty">Nenhum cliente cadastrado.</div>}
    </div>
    {creating&&<CustomerSheet onClose={()=>setCreating(false)} onSaved={async()=>{setCreating(false);await load()}}/>}
  </div>
}

function CustomerSheet({onClose,onSaved}:{onClose:()=>void;onSaved:()=>void}){
  const [name,setName]=useState('')
  const [phone,setPhone]=useState('')
  const [notes,setNotes]=useState('')
  const [saving,setSaving]=useState(false)
  const [error,setError]=useState('')

  async function save(){
    setSaving(true);setError('')
    const r=await fetch('/api/app/customers',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,phone,notes})})
    const b=await r.json().catch(()=>({}))
    if(!r.ok){setError(b.error||'Não foi possível criar o cliente.');setSaving(false);return}
    await onSaved()
  }

  return <div className="sheet" onMouseDown={e=>{if(e.currentTarget===e.target)onClose()}}>
    <div className="sheetPanel"><div className="sheetHandle"/><h2>Novo cliente</h2>
      {error&&<div className="inlineError">{error}</div>}
      <div className="formGrid">
        <div className="formField"><label>Nome</label><input autoFocus value={name} onChange={e=>setName(e.target.value)}/></div>
        <div className="formField"><label>WhatsApp / telefone</label><input inputMode="tel" value={phone} onChange={e=>setPhone(e.target.value)} placeholder="+55 11 99999-9999"/></div>
        <div className="formField"><label>Observações</label><textarea value={notes} onChange={e=>setNotes(e.target.value)} placeholder="Preferências, observações importantes..."/></div>
        <button className="touchButton primary" disabled={!name||!phone||saving} onClick={save}>{saving?'Salvando…':'Salvar cliente'}</button>
      </div>
    </div>
  </div>
}
