'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'
import { useAvailableSlots } from '@/lib/use-available-slots'
import { createCommandKeys } from '@/lib/booking-request'

type Appointment = {
  id: string; date: string; startTime: string; endTime: string; status: string;
  customerId: string; serviceId: string; professionalId: string;
  customerName: string; serviceName: string; professionalName: string
}
const labels: Record<string, string> = { PENDENTE: 'Pendente', CONFIRMADO: 'Confirmado', CANCELADO: 'Cancelado', CONCLUIDO: 'Concluído' }
function localIso(d = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(d)
}
function shiftDay(date: string, offset: number) {
  const d = new Date(date + 'T12:00:00Z')
  d.setUTCDate(d.getUTCDate() + offset)
  return d.toISOString().slice(0, 10)
}
const dateLabel = (date: string) => new Date(date + 'T12:00:00Z').toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: 'short', timeZone: 'America/Sao_Paulo' })

export default function AgendaPage() {
  const [date, setDate] = useState(localIso)
  const [mode, setMode] = useState<'day' | 'week'>('day')
  const [result, setResult] = useState({ key: '', items: [] as Appointment[] })
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')
  const pending = useRef(false)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [revision, setRevision] = useState(0)
  const [professional, setProfessional] = useState('')
  const [status, setStatus] = useState('')
  const [rescheduling, setRescheduling] = useState<Appointment | null>(null)
  const queryKey = `${date}:${mode}:${revision}`

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setLoadError('')
    const days = Array.from({ length: mode === 'week' ? 7 : 1 }, (_, i) => shiftDay(date, i))
    Promise.all(days.map(async day => {
      const r = await fetch('/api/app/appointments?date=' + day, { cache: 'no-store', signal: controller.signal })
      if (!r.ok) throw new Error()
      const data: unknown = await r.json()
      if (!Array.isArray(data)) throw new Error()
      return data as Appointment[]
    })).then(data => {
      if (!controller.signal.aborted) setResult({ key: queryKey, items: data.flat().sort((a, b) => (a.date + a.startTime).localeCompare(b.date + b.startTime)) })
    }).catch(() => {
      if (!controller.signal.aborted) setLoadError('Não foi possível carregar a agenda.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [date, mode, revision, queryKey])

  async function cancel(id: string) {
    if (pending.current || !confirm('Cancelar este agendamento?')) return
    pending.current = true; setBusy(id); setError('')
    try {
      const r = await fetch(`/api/app/appointments/${id}/cancel`, { method: 'POST' })
      const body = await r.json().catch(() => ({}))
      if (!r.ok) throw new Error(body.error || 'Não foi possível cancelar.')
      setRevision(x => x + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Sem resposta do servidor. Atualize a agenda para conferir o cancelamento.')
    } finally { pending.current = false; setBusy('') }
  }

  const items = result.key === queryKey ? result.items : []
  const professionals = [...new Map(items.map(a => [a.professionalId, a.professionalName])).entries()]
  const filtered = items.filter(a => (!professional || a.professionalId === professional) && (!status || a.status === status))
  return <div className="page">
    <div className="eyebrow">Operação</div>
    <div className="sectionHead"><div><h1 className="title">Agenda</h1><p className="subtitle">Sua agenda, em todos os canais.</p></div><Link href="/novo" className="touchButton primary">+ Novo</Link></div>
    <div className="agendaToolbar">
      <button className="touchButton secondary" onClick={() => setDate(localIso())}>Hoje</button>
      <button className="touchButton secondary" aria-label="Período anterior" onClick={() => setDate(shiftDay(date, mode === 'week' ? -7 : -1))}>←</button>
      <input aria-label="Data da agenda" type="date" value={date} onChange={e => { if (e.target.value) setDate(e.target.value) }} />
      <button className="touchButton secondary" aria-label="Próximo período" onClick={() => setDate(shiftDay(date, mode === 'week' ? 7 : 1))}>→</button>
      <select aria-label="Visualização" value={mode} onChange={e => setMode(e.target.value as 'day' | 'week')}><option value="day">Dia</option><option value="week">Semana</option></select>
      <select aria-label="Filtrar profissional" value={professional} onChange={e => setProfessional(e.target.value)}><option value="">Toda a equipe</option>{professionals.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select>
      <select aria-label="Filtrar status" value={status} onChange={e => setStatus(e.target.value)}><option value="">Todos os status</option>{[...new Set(items.map(a => a.status))].map(value => <option key={value} value={value}>{labels[value] || value}</option>)}</select>
      <button className="touchButton secondary" disabled={loading} onClick={() => setRevision(x => x + 1)}>Atualizar</button>
    </div>
    {error && <div className="inlineError" role="alert">{error}</div>}
    {loadError ? <div className="inlineError" role="alert">{loadError} <button onClick={() => setRevision(x => x + 1)} className="touchButton secondary">Tentar novamente</button></div> : loading || result.key !== queryKey ? <div className="card" role="status">Carregando agenda…</div> : <div className="appointmentCards">{filtered.length ? filtered.map(a => <article className="appointmentCard" key={a.id}>
      <div className="appointmentTop"><div><div className="label">{dateLabel(a.date)}</div><div className="appointmentTime">{a.startTime.slice(0, 5)}</div><strong>{a.customerName}</strong></div><span className="pill">{labels[a.status] || a.status}</span></div>
      <div className="appointmentMeta">{a.serviceName}<br />{a.professionalName} · até {a.endTime.slice(0, 5)}</div>
      {!['CANCELADO', 'CONCLUIDO'].includes(a.status) && <div className="actionRow" style={{ marginTop: 12 }}>
        <button className="touchButton secondary" disabled={!!busy} onClick={() => setRescheduling(a)}>Reagendar</button>
        <button className="touchButton danger" disabled={!!busy} onClick={() => cancel(a.id)}>{busy === a.id ? 'Cancelando…' : 'Cancelar'}</button>
      </div>}
    </article>) : <div className="card empty">Nenhum agendamento {professional || status ? 'com esses filtros' : 'neste período'}. <Link href="/novo">Criar agendamento</Link></div>}</div>}
    {rescheduling && <RescheduleSheet item={rescheduling} onClose={() => setRescheduling(null)} onSaved={() => { setRescheduling(null); setRevision(x => x + 1) }} />}
  </div>
}

function RescheduleSheet({ item, onClose, onSaved }: { item: Appointment; onClose: () => void; onSaved: () => void }) {
  const [date, setDate] = useState(item.date)
  const availability = useAvailableSlots(item.serviceId, item.professionalId, date)
  const [selection, setSelection] = useState({ query: '', time: '' })
  const time = selection.query === availability.query && availability.slots.includes(selection.time) ? selection.time : ''
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const pending = useRef(false)
  const commandKey = useRef(createCommandKeys())
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal() }, [])

  async function save() {
    if (pending.current || !time || availability.loading) return
    pending.current = true; setSaving(true); setError('')
    try {
      const command = { appointmentId: item.id, date, time }
      const r = await fetch(`/api/app/appointments/${item.id}/reschedule`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ date, time, idempotencyKey: commandKey.current(command) })
      })
      const body = await r.json().catch(() => ({}))
      if (!r.ok) throw new Error(body.error || 'Não foi possível reagendar.')
      onSaved()
    } catch (e) { setError(e instanceof Error ? e.message : 'Sem resposta do servidor. Confira a agenda antes de alterar os dados.') }
    finally { pending.current = false; setSaving(false) }
  }

  return <dialog ref={dialog} className="rescheduleDialog" aria-labelledby="reschedule-title" onCancel={e => { if (pending.current) e.preventDefault(); else onClose() }}>
    <div className="sectionHead"><h2 id="reschedule-title">Reagendar</h2><button className="touchButton secondary" disabled={saving} onClick={onClose}>Fechar</button></div>
    <strong>{item.customerName}</strong><p className="subtitle">{item.serviceName} · {item.professionalName}</p>
    {error && <div className="inlineError" role="alert">{error}</div>}
    <div className="formGrid">
      <div className="formField"><label htmlFor="reschedule-date">Nova data</label><input id="reschedule-date" disabled={saving} type="date" min={localIso()} value={date} onChange={e => { setDate(e.target.value); setSelection({ query: '', time: '' }) }} /></div>
      <fieldset className="slotField"><legend>Novo horário</legend>
        {availability.loading ? <div role="status">Consultando horários…</div> : availability.error ? <div role="alert">{availability.error} <button className="touchButton secondary" onClick={availability.retry}>Tentar novamente</button></div> : availability.slots.length ? <div className="actionRow">{availability.slots.map(s => <button disabled={saving} key={s} aria-pressed={time === s} className={time === s ? 'touchButton primary' : 'touchButton secondary'} onClick={() => setSelection({ query: availability.query, time: s })}>{s.slice(0, 5)}</button>)}</div> : <div className="inlineNotice">Sem horários livres nessa data.</div>}
      </fieldset>
      <button className="touchButton primary" disabled={!time || saving || availability.loading} onClick={save}>{saving ? 'Reagendando…' : 'Confirmar novo horário'}</button>
    </div>
  </dialog>
}
