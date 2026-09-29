import { NextResponse } from 'next/server'
import { isSameOriginMutation } from './request-safety'

export function rejectCrossOrigin(request: Request) {
  const origin = process.env.TROQUIM_PUBLIC_ORIGIN
  return isSameOriginMutation(request, origin) ? null : NextResponse.json(
    { error: 'Não foi possível validar a origem. Reabra o Troquim e tente novamente.' },
    { status: 403, headers: { 'Cache-Control': 'no-store' } }
  )
}
