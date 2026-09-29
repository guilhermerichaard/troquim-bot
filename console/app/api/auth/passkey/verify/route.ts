import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { ownerBackendUrl } from '@/lib/troquim'
import { rejectCrossOrigin } from '@/lib/csrf'
import { attachOwnerCookie, backendCookie, ceremonyCookieName, clearCeremonyCookie } from '@/lib/passkey-proxy'

export async function POST(request: Request) {
  const rejected = rejectCrossOrigin(request)
  if (rejected) return rejected
  const jar = await cookies()
  const ceremony = jar.get(ceremonyCookieName)?.value
  if (!ceremony) return NextResponse.json({ error: 'Cerimônia expirada. Tente novamente.' }, { status: 401 })

  const backend = await fetch(`${ownerBackendUrl()}/login/webauthn`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Cookie: backendCookie(undefined, ceremony) },
    body: await request.text(),
    cache: 'no-store',
    redirect: 'manual',
  })
  const text = await backend.text()
  const out = new NextResponse(text || null, {
    status: backend.status,
    headers: { 'Content-Type': backend.headers.get('content-type') || 'application/json', 'Cache-Control': 'no-store' },
  })
  clearCeremonyCookie(out)
  if (backend.ok && !attachOwnerCookie(out, backend)) {
    return NextResponse.json({ error: 'Sessão não retornada pelo backend.' }, { status: 502 })
  }
  return out
}
