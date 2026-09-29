import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { ownerBackendUrl, ownerCookieName } from '@/lib/troquim'
import { rejectCrossOrigin } from '@/lib/csrf'
import { attachCeremonyCookie, backendCookie } from '@/lib/passkey-proxy'

export async function POST(request: Request) {
  const rejected = rejectCrossOrigin(request)
  if (rejected) return rejected
  const jar = await cookies()
  const owner = jar.get(ownerCookieName)?.value
  if (!owner) return NextResponse.json({ error: 'Sessão expirada.' }, { status: 401 })

  const backend = await fetch(`${ownerBackendUrl()}/webauthn/register/options`, {
    method: 'POST',
    headers: { Cookie: backendCookie(owner) },
    cache: 'no-store',
  })
  const text = await backend.text()
  const out = new NextResponse(text || null, {
    status: backend.status,
    headers: { 'Content-Type': backend.headers.get('content-type') || 'application/json', 'Cache-Control': 'no-store' },
  })
  attachCeremonyCookie(out, backend)
  return out
}
