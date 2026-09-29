'use client'

import { FormEvent, useEffect, useRef, useState } from 'react'

type Session = { id: string; createdAt: string; expiresAt: string; current: boolean }
type WhatsAppState = { enabled: boolean; phone: string }

const label = (value: string) => {
  const [date, time] = value.split('T')
  return `${date.split('-').reverse().join('/')} às ${time.slice(0, 5)}`
}

export default function SecurityPage() {
  const [sessions, setSessions] = useState<Session[]>([])
  const [whatsapp, setWhatsapp] = useState<WhatsAppState>({ enabled: false, phone: '' })
  const [challengeId, setChallengeId] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [revision, setRevision] = useState(0)
  const pending = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError('')
    Promise.all([
      fetch('/api/app/security/sessions', { cache: 'no-store', signal: controller.signal }).then(async r => { if (!r.ok) throw new Error(); return r.json() }),
      fetch('/api/app/security/whatsapp', { cache: 'no-store', signal: controller.signal }).then(async r => { if (!r.ok) throw new Error(); return r.json() }),
    ]).then(([sessionData, whatsappData]) => {
      if (!controller.signal.aborted) { setSessions(sessionData); setWhatsapp(whatsappData) }
    }).catch(() => {
      if (!controller.signal.aborted) setError('Não foi possível consultar as configurações de segurança.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [revision])

  async function revoke(id?: string) {
    if (pending.current || !confirm(id ? 'Encerrar esta sessão?' : 'Sair de todos os outros dispositivos?')) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try {
      const r = await fetch(`/api/app/security/sessions/${id || 'revoke-others'}`, { method: id ? 'DELETE' : 'POST' })
      if (!r.ok) throw new Error()
      setRevision(x => x + 1)
    } catch { setError('Não foi possível encerrar as sessões. Tente novamente.') }
    finally { pending.current = false; setBusy(false) }
  }

  async function requestPhone(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (pending.current) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    const data = new FormData(e.currentTarget)
    try {
      const r = await fetch('/api/app/security/whatsapp/request', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone: data.get('phone') }),
      })
      const body = await r.json().catch(() => ({}))
      if (!r.ok || !body.challengeId) throw new Error(body.error || '')
      setChallengeId(body.challengeId)
      setNotice('Código enviado pelo WhatsApp.')
    } catch { setError('Não foi possível enviar o código para esse número.') }
    finally { pending.current = false; setBusy(false) }
  }

  async function verifyPhone(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (pending.current) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    const data = new FormData(e.currentTarget)
    try {
      const r = await fetch('/api/app/security/whatsapp/verify', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ challengeId, code: data.get('code') }),
      })
      if (!r.ok) throw new Error()
      setChallengeId('')
      setNotice('WhatsApp verificado. Agora ele pode ser usado para entrar no Troquim.')
      setRevision(x => x + 1)
    } catch { setError('Código inválido ou expirado.') }
    finally { pending.current = false; setBusy(false) }
  }

  return <div className="page">
    <div className="eyebrow">Configurações</div><h1 className="title">Segurança</h1>
    <p className="subtitle">Gerencie seus acessos e formas de entrar no Troquim.</p>
    {error && <div role="alert" className="inlineError">{error} <button className="touchButton secondary" onClick={() => setRevision(x => x + 1)}>Tentar novamente</button></div>}
    {notice && <div className="inlineNotice" role="status">{notice}</div>}

    {loading ? <div role="status" className="card">Consultando segurança…</div> : <>
      <section className="settingCard" style={{ marginBottom: 20 }}>
        <div className="sectionHead"><div><strong>Entrar com WhatsApp</strong><div className="label">{whatsapp.phone ? `Número verificado: ${whatsapp.phone}` : 'Vincule seu número pessoal para receber códigos de acesso.'}</div></div></div>
        {!whatsapp.enabled ? <div className="inlineNotice">Disponível depois que o template de autenticação do WhatsApp estiver aprovado e ativado.</div> :
          challengeId ? <form className="formGrid" onSubmit={verifyPhone}>
            <div className="formField"><label htmlFor="security-code">Código de 6 dígitos</label><input id="security-code" name="code" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" required /></div>
            <div className="actionRow"><button className="touchButton primary" disabled={busy}>{busy ? 'Verificando…' : 'Confirmar número'}</button><button type="button" className="touchButton secondary" disabled={busy} onClick={() => setChallengeId('')}>Cancelar</button></div>
          </form> : <form className="formGrid" onSubmit={requestPhone}>
            <div className="formField"><label htmlFor="security-phone">{whatsapp.phone ? 'Trocar WhatsApp' : 'Seu WhatsApp'}</label><input id="security-phone" name="phone" type="tel" autoComplete="tel" inputMode="tel" placeholder="+55 11 99999-9999" required /></div>
            <button className="touchButton primary" disabled={busy}>{busy ? 'Enviando…' : whatsapp.phone ? 'Verificar novo número' : 'Vincular WhatsApp'}</button>
          </form>}
      </section>

      <section>
        <div className="sectionHead"><div><strong>Sessões</strong><div className="label">Acessos atuais à sua conta.</div></div>
          <button className="touchButton danger" disabled={busy || !sessions.some(s => !s.current)} onClick={() => revoke()}>{busy ? 'Encerrando…' : 'Sair de outros dispositivos'}</button>
        </div>
        <div className="settingsGrid" style={{ marginTop: 20 }}>{sessions.map(s => <article className="settingCard" key={s.id}>
          <div className="sectionHead"><strong>{s.current ? 'Sessão atual' : 'Outra sessão'}</strong>{!s.current && <button className="touchButton secondary" disabled={busy} onClick={() => revoke(s.id)}>Encerrar</button>}</div>
          <div className="label">Iniciada em {label(s.createdAt)}<br />Expira em {label(s.expiresAt)}</div>
        </article>)}</div>
      </section>
    </>}
  </div>
}
