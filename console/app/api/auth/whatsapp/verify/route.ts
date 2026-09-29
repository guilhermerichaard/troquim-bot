import { NextResponse } from 'next/server'
import { ownerBackendUrl, ownerCookieName } from '@/lib/troquim'
import { rejectCrossOrigin } from '@/lib/csrf'

export async function POST(request: Request) {
  const rejected = rejectCrossOrigin(request)
  if (rejected) return rejected

  const backend = await fetch(`${ownerBackendUrl()}/api/v1/owner/otp/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: await request.text(),
    cache: 'no-store',
  })

  if (!backend.ok) {
    return NextResponse.json({ error: 'Código inválido ou expirado.' }, { status: backend.status })
  }

  const setCookie = backend.headers.get('set-cookie') ?? ''
  const match = setCookie.match(new RegExp(`${ownerCookieName}=([^;]+)`))
  if (!match) {
    return NextResponse.json({ error: 'Sessão não retornada pelo backend.' }, { status: 502 })
  }

  const response = NextResponse.json({ ok: true })
  response.cookies.set(ownerCookieName, match[1], {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    path: '/',
    maxAge: 12 * 60 * 60,
  })
  return response
}
