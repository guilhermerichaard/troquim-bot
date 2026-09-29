'use client'

import { FormEvent, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'

type Mode = 'whatsapp' | 'password'

export default function LoginPage(){
  const router=useRouter()
  const [error,setError]=useState('')
  const [loading,setLoading]=useState(false)
  const [otpEnabled,setOtpEnabled]=useState(false)
  const [mode,setMode]=useState<Mode>('password')
  const [challengeId,setChallengeId]=useState('')
  const [phone,setPhone]=useState('')

  useEffect(()=>{
    fetch('/api/auth/whatsapp/status',{cache:'no-store'})
      .then(r=>r.json())
      .then(data=>{ if(data.enabled){setOtpEnabled(true);setMode('whatsapp')} })
      .catch(()=>{})
  },[])

  async function passwordLogin(e:FormEvent<HTMLFormElement>){
    e.preventDefault(); setLoading(true); setError('')
    const data=new FormData(e.currentTarget)
    const response=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:data.get('email'),senha:data.get('senha')})})
    const body=await response.json().catch(()=>({}))
    if(!response.ok){setError(body.error||'Não foi possível entrar.');setLoading(false);return}
    router.push('/'); router.refresh()
  }

  async function requestCode(e:FormEvent<HTMLFormElement>){
    e.preventDefault(); setLoading(true); setError('')
    const data=new FormData(e.currentTarget)
    const value=String(data.get('phone')||'')
    const response=await fetch('/api/auth/whatsapp/request',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({phone:value})})
    const body=await response.json().catch(()=>({}))
    setLoading(false)
    if(!response.ok || !body.challengeId){setError('Não foi possível enviar o código.');return}
    setPhone(value); setChallengeId(body.challengeId)
  }

  async function verifyCode(e:FormEvent<HTMLFormElement>){
    e.preventDefault(); setLoading(true); setError('')
    const data=new FormData(e.currentTarget)
    const code=String(data.get('code')||'')
    const response=await fetch('/api/auth/whatsapp/verify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({challengeId,code})})
    const body=await response.json().catch(()=>({}))
    if(!response.ok){setError(body.error||'Código inválido ou expirado.');setLoading(false);return}
    router.push('/'); router.refresh()
  }

  return <main className="login">
    <div className="loginCard">
      <div className="eyebrow">Troquim</div>
      <h1>Seu negócio, em um lugar.</h1>
      <p className="subtitle">Entre com segurança para continuar.</p>

      {otpEnabled&&<div className="actionRow" style={{marginBottom:20}}>
        <button type="button" className={mode==='whatsapp'?'touchButton primary':'touchButton secondary'} onClick={()=>{setMode('whatsapp');setError('')}}>WhatsApp</button>
        <button type="button" className={mode==='password'?'touchButton primary':'touchButton secondary'} onClick={()=>{setMode('password');setError('')}}>E-mail e senha</button>
      </div>}

      {mode==='whatsapp'&&otpEnabled ? (
        challengeId ? <form onSubmit={verifyCode}>
          <div className="field"><label htmlFor="code">Código de 6 dígitos</label><input id="code" name="code" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" autoFocus required/></div>
          <p className="subtitle">Enviamos o código para {phone}. No WhatsApp, toque em “Copiar código” e cole aqui.</p>
          <button className="primary" disabled={loading}>{loading?'Verificando…':'Entrar'}</button>
          <button type="button" className="touchButton secondary" disabled={loading} onClick={()=>{setChallengeId('');setError('')}}>Trocar número</button>
        </form> : <form onSubmit={requestCode}>
          <div className="field"><label htmlFor="phone">Seu WhatsApp</label><input id="phone" name="phone" type="tel" inputMode="tel" autoComplete="tel" placeholder="+55 11 99999-9999" required/></div>
          <button className="primary" disabled={loading}>{loading?'Enviando…':'Receber código'}</button>
        </form>
      ) : <form onSubmit={passwordLogin}>
        <div className="field"><label htmlFor="email">E-mail</label><input id="email" name="email" type="email" required autoComplete="email"/></div>
        <div className="field"><label htmlFor="senha">Senha</label><input id="senha" name="senha" type="password" required autoComplete="current-password"/></div>
        <button className="primary" disabled={loading}>{loading?'Entrando…':'Entrar'}</button>
      </form>}
      {error&&<div className="error" role="alert">{error}</div>}
    </div>
  </main>
}
