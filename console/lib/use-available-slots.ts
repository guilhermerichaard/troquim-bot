'use client'

import { useEffect, useState } from 'react'
import { slotsQuery } from './booking-request'

export function useAvailableSlots(serviceId: string, professionalId: string, date: string) {
  const query = slotsQuery(serviceId, professionalId, date)
  const [result, setResult] = useState({ query: '', slots: [] as string[], error: '' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!query) return
    const controller = new AbortController()
    setResult({ query: '', slots: [], error: '' })
    fetch(`/api/app/slots?${query}`, { cache: 'no-store', signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error()
        const slots: unknown = await response.json()
        if (!Array.isArray(slots) || !slots.every(x => typeof x === 'string')) throw new Error()
        if (!controller.signal.aborted) setResult({ query, slots, error: '' })
      })
      .catch(() => {
        if (!controller.signal.aborted) setResult({ query, slots: [], error: 'Não foi possível consultar horários. Tente novamente.' })
      })
    return () => controller.abort()
  }, [query, attempt])

  const current = query !== '' && result.query === query
  return {
    query,
    slots: current ? result.slots : [],
    error: current ? result.error : '',
    loading: query !== '' && !current,
    retry() { setResult({ query: '', slots: [], error: '' }); setAttempt(x => x + 1) },
  }
}
