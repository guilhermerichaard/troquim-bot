'use client'

import { useEffect, useState } from 'react'

type Service={id:string;name:string;description:string|null;durationMinutes:number;price:number|null;status:string}

export default function ServicosPage(){
 const [items,setItems]=useState<Service[]>([])
 const [editing,setEditing]=useState<Service|null>(null)
 const [creating,setCreating]=useState(false)
 const [error,setError]=useState('')
 async function load(){const r=await fetch('/api/app/services',{cache:'no-store'});if(r.ok)setItems(await r.json())}
 useEffect(()=>{void load()},[])
 async function status(x:Service){
  const r=await fetch(`/api/app/services/${x.id}/status`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({active:x.status!=='ATIVO'})})
  if(!r.ok){setError('Não foi possível alterar o serviço.');return} await load()
 }
 return <div className="page"><div className="sectionHead"><div><div className="eyebrow">Catálogo</div><h1 className="title">Serviços</h1></div><button className="touchButton primary" onClick={()=>setCreating(true)}>+ Novo</button></div>
 {error&&<div className="inlineError">{error}</div>}
 <div className="appointmentCards">{items.map(x=><article className="appointmentCard" key={x.id}><div className="appointmentTop"><div><strong>{x.name}</strong><div className="appointmentMeta">{x.durationMinutes} min{x.price!=null?` · ${x.price.toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}`:''}</div></div><span className="pill">{x.status}</span></div><div className="actionRow" style={{marginTop:12}}><button className="touchButton secondary" onClick={()=>setEditing(x)}>Editar</button><button className="touchButton secondary" onClick={()=>status(x)}>{x.status==='ATIVO'?'Pausar':'Ativar'}</button></div></article>)}</div>
 {(editing||creating)&&<ServiceSheet item={editing} onClose={()=>{setEditing(null);setCreating(false)}} onSaved={async()=>{setEditing(null);setCreating(false);await load()}}/>}
 </div>
}

function ServiceSheet({item,onClose,onSaved}:{item:Service|null;onClose:()=>void;onSaved:()=>void}){
 const [name,setName]=useState(item?.name??'');const [description,setDescription]=useState(item?.description??'');const [duration,setDuration]=useState(item?.durationMinutes??60);const [price,setPrice]=useState(item?.price?.toString()??'');const [error,setError]=useState('')
 async function save(){const url=item?`/api/app/services/${item.id}`:'/api/app/services';const r=await fetch(url,{method:item?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,description,durationMinutes:Number(duration),price:price===''?null:Number(price)})});const b=await r.json().catch(()=>({}));if(!r.ok){setError(b.error||'Não foi possível salvar.');return}onSaved()}
 return <div className="sheet" onMouseDown={e=>{if(e.currentTarget===e.target)onClose()}}><div className="sheetPanel"><div className="sheetHandle"/><h2>{item?'Editar serviço':'Novo serviço'}</h2>{error&&<div className="inlineError">{error}</div>}<div className="formGrid"><div className="formField"><label>Nome</label><input value={name} onChange={e=>setName(e.target.value)}/></div><div className="formField"><label>Descrição</label><textarea value={description} onChange={e=>setDescription(e.target.value)}/></div><div className="formField"><label>Duração (min)</label><input type="number" min="5" step="5" value={duration} onChange={e=>setDuration(Number(e.target.value))}/></div><div className="formField"><label>Preço (opcional)</label><input inputMode="decimal" value={price} onChange={e=>setPrice(e.target.value)}/></div><button className="touchButton primary" onClick={save}>Salvar</button></div></div></div>
}
