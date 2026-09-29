'use client'

import { useEffect, useRef, useState } from 'react'

type Session = { id: string; createdAt: string; expiresAt: string; current: boolean }
const label = (value: string) => {
  const [date, time] = value.split('T')
  return `${date.split('-').reverse().join('/')} às ${time.slice(0, 5)}`
}
export default function SecurityPage() {
  const [sessions, setSessions] = useState<Session[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [revision, setRevision] = useState(0)
  const pending = useRef(false)
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError('')
    fetch('/api/app/security/sessions', { cache: 'no-store', signal: controller.signal })
      .then(async r => { if (!r.ok) throw new Error(); return r.json() })
      .then(data => { if (!controller.signal.aborted) setSessions(data) })
      .catch(() => { if (!controller.signal.aborted) setError('Não foi possível consultar as sessões.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [revision])

  async function revoke(id?: string) {
    if (pending.current || !confirm(id ? 'Encerrar esta sessão?' : 'Sair de todos os outros dispositivos?')) return
    pending.current = true; setBusy(true); setError('')
    try {
      const r = await fetch(`/api/app/security/sessions/${id || 'revoke-others'}`, { method: id ? 'DELETE' : 'POST' })
      if (!r.ok) throw new Error()
      setRevision(x => x + 1)
    } catch { setError('Não foi possível encerrar as sessões. Tente novamente.') }
    finally { pending.current = false; setBusy(false) }
  }

  return <div className="page">
    <div className="eyebrow">Configurações</div><h1 className="title">Segurança</h1>
    <p className="subtitle">Confira seus acessos e encerre os que você não usa mais.</p>
    {error && <div role="alert" className="inlineError">{error} <button className="touchButton secondary" onClick={() => setRevision(x => x + 1)}>Tentar novamente</button></div>}
    {loading ? <div role="status" className="card">Consultando sessões…</div> : <>
      <button className="touchButton danger" disabled={busy || !sessions.some(s => !s.current)} onClick={() => revoke()}>{busy ? 'Encerrando…' : 'Sair de outros dispositivos'}</button>
      <div className="settingsGrid" style={{ marginTop: 20 }}>{sessions.map(s => <article className="settingCard" key={s.id}>
        <div className="sectionHead"><strong>{s.current ? 'Sessão atual' : 'Outra sessão'}</strong>{!s.current && <button className="touchButton secondary" disabled={busy} onClick={() => revoke(s.id)}>Encerrar</button>}</div>
        <div className="label">Iniciada em {label(s.createdAt)}<br />Expira em {label(s.expiresAt)}</div>
      </article>)}</div>
    </>}
  </div>
}
