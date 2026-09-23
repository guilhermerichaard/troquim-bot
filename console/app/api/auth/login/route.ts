import { NextResponse } from 'next/server'
import { ownerBackendUrl, ownerCookieName } from '@/lib/troquim'

export async function POST(request: Request) {
  const body = await request.text()
  const backend = await fetch(`${ownerBackendUrl()}/api/v1/owner/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    cache: 'no-store',
  })

  if (!backend.ok) {
    return NextResponse.json(
      { error: backend.status === 401 ? 'E-mail ou senha inválidos.' : 'Não foi possível entrar.' },
      { status: backend.status }
    )
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
