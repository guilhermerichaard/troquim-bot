'use client'

import { FormEvent, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { getPasskey, passkeysSupported } from '@/lib/passkeys'

type Mode = 'whatsapp' | 'password'

export default function LoginPage(){
  const router=useRouter()
  const [error,setError]=useState('')
  const [loading,setLoading]=useState(false)
  const [otpEnabled,setOtpEnabled]=useState(false)
  const [passkeyEnabled,setPasskeyEnabled]=useState(false)
  const [mode,setMode]=useState<Mode>('password')
  const [challengeId,setChallengeId]=useState('')
  const [phone,setPhone]=useState('')

  useEffect(()=>{
    Promise.all([
      fetch('/api/auth/whatsapp/status',{cache:'no-store'}).then(r=>r.json()).catch(()=>({enabled:false})),
      fetch('/api/auth/passkey/status',{cache:'no-store'}).then(r=>r.json()).catch(()=>({enabled:false})),
    ]).then(([otp,passkey])=>{
      if(otp.enabled){setOtpEnabled(true);setMode('whatsapp')}
      if(passkey.enabled&&passkeysSupported()) setPasskeyEnabled(true)
    })
  },[])

  async function passkeyLogin(){
    setLoading(true);setError('')
    try{
      const optionsResponse=await fetch('/api/auth/passkey/options',{method:'POST'})
      if(!optionsResponse.ok) throw new Error()
      const options=await optionsResponse.json()
      const credential=await getPasskey(options)
      const verify=await fetch('/api/auth/passkey/verify',{
        method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(credential)
      })
      if(!verify.ok) throw new Error()
      router.push('/');router.refresh()
    }catch{
      setError('Não foi possível entrar com a entrada rápida. Use WhatsApp ou senha.')
      setLoading(false)
    }
  }

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

      {passkeyEnabled&&<button type="button" className="primary" disabled={loading} onClick={passkeyLogin} style={{marginBottom:16}}>
        {loading?'Abrindo entrada rápida…':'Entrar com Face ID / digital'}
      </button>}

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
        <div className="field"><label htmlFor="email">E-mail</label><input id="email" name="email" type="email" required autoComplete={passkeyEnabled?'username webauthn':'email'}/></div>
        <div className="field"><label htmlFor="senha">Senha</label><input id="senha" name="senha" type="password" required autoComplete="current-password"/></div>
        <button className="primary" disabled={loading}>{loading?'Entrando…':'Entrar'}</button>
      </form>}
      {error&&<div className="error" role="alert">{error}</div>}
    </div>
  </main>
}
