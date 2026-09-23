'use client'

import { FormEvent, useMemo, useState } from 'react'
import { Bot, MessageCircle, Send, X } from 'lucide-react'
import { usePathname } from 'next/navigation'

type ChatMessage={role:'assistant'|'user';text:string}

const quick=[
  'Como criar um agendamento?',
  'Como reagendar um cliente?',
  'Como cadastrar um serviço?',
  'WhatsApp não está conectado'
]

export function OwnerAssistant(){
  const pathname=usePathname()
  const [open,setOpen]=useState(false)
  const [input,setInput]=useState('')
  const [loading,setLoading]=useState(false)
  const [messages,setMessages]=useState<ChatMessage[]>([
    {role:'assistant',text:'Oi! Eu sou o assistente do Troquim. Posso te ajudar a usar a agenda, cadastrar serviços, organizar clientes e resolver dúvidas do painel.'}
  ])

  const page=useMemo(()=>{
    if(pathname.startsWith('/agenda')) return 'agenda'
    if(pathname.startsWith('/clientes')) return 'clientes'
    if(pathname.startsWith('/servicos')) return 'servicos'
    if(pathname.startsWith('/equipe')) return 'equipe'
    if(pathname.startsWith('/novo')) return 'novo-agendamento'
    return 'hoje'
  },[pathname])

  async function ask(text:string){
    const clean=text.trim()
    if(!clean||loading)return
    setMessages(v=>[...v,{role:'user',text:clean}])
    setInput('')
    setLoading(true)
    try{
      const r=await fetch('/api/help',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message:clean,page})})
      const body=await r.json().catch(()=>({}))
      setMessages(v=>[...v,{role:'assistant',text:body.answer||'Não consegui responder agora. Tente novamente em instantes.'}])
    }catch{
      setMessages(v=>[...v,{role:'assistant',text:'Não consegui responder agora. Tente novamente em instantes.'}])
    }finally{setLoading(false)}
  }

  function submit(e:FormEvent){e.preventDefault();void ask(input)}

  return <>
    <button className="assistantFab" aria-label="Abrir assistente do Troquim" onClick={()=>setOpen(true)}>
      <MessageCircle size={24}/>
      <span>Ajuda</span>
    </button>

    {open&&<div className="assistantBackdrop" onMouseDown={e=>{if(e.currentTarget===e.target)setOpen(false)}}>
      <section className="assistantPanel" aria-label="Assistente do Troquim">
        <header className="assistantHeader">
          <div className="assistantIdentity"><span className="assistantAvatar"><Bot size={20}/></span><div><strong>Assistente Troquim</strong><small>Ajuda operacional</small></div></div>
          <button className="assistantClose" aria-label="Fechar" onClick={()=>setOpen(false)}><X size={20}/></button>
        </header>

        <div className="assistantMessages">
          {messages.map((m,i)=><div key={i} className={m.role==='user'?'assistantMessage user':'assistantMessage'}>{m.text}</div>)}
          {loading&&<div className="assistantMessage typing">Digitando…</div>}
        </div>

        <div className="assistantQuick">
          {quick.map(q=><button key={q} onClick={()=>void ask(q)}>{q}</button>)}
        </div>

        <form className="assistantComposer" onSubmit={submit}>
          <input value={input} onChange={e=>setInput(e.target.value)} placeholder="Pergunte sobre o Troquim…" aria-label="Mensagem para o assistente"/>
          <button aria-label="Enviar" disabled={!input.trim()||loading}><Send size={18}/></button>
        </form>
      </section>
    </div>}
  </>
}
