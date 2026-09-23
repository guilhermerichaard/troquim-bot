'use client'

import { FormEvent, useState } from 'react'
import { useRouter } from 'next/navigation'

export default function LoginPage(){
  const router=useRouter()
  const [error,setError]=useState('')
  const [loading,setLoading]=useState(false)

  async function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault(); setLoading(true); setError('')
    const data=new FormData(e.currentTarget)
    const response=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:data.get('email'),senha:data.get('senha')})})
    const body=await response.json().catch(()=>({}))
    if(!response.ok){setError(body.error||'Não foi possível entrar.');setLoading(false);return}
    router.push('/'); router.refresh()
  }

  return <main className="login"><form className="loginCard" onSubmit={submit}>
    <div className="eyebrow">Troquim Console</div><h1>Seu negócio, em um lugar.</h1><p className="subtitle">Entre com o owner provisionado no Troquim.</p>
    <div className="field"><label>E-mail</label><input name="email" type="email" required autoComplete="email"/></div>
    <div className="field"><label>Senha</label><input name="senha" type="password" required autoComplete="current-password"/></div>
    <button className="primary" disabled={loading}>{loading?'Entrando…':'Entrar'}</button>
    {error&&<div className="error">{error}</div>}
  </form></main>
}
