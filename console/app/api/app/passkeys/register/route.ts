import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { ownerBackendUrl, ownerCookieName } from '@/lib/troquim'
import { rejectCrossOrigin } from '@/lib/csrf'
import { backendCookie, ceremonyCookieName, clearCeremonyCookie } from '@/lib/passkey-proxy'

export async function POST(request: Request) {
  const rejected = rejectCrossOrigin(request)
  if (rejected) return rejected
  const jar = await cookies()
  const owner = jar.get(ownerCookieName)?.value
  const ceremony = jar.get(ceremonyCookieName)?.value
  if (!owner || !ceremony) return NextResponse.json({ error: 'Cerimônia expirada.' }, { status: 401 })

  const backend = await fetch(`${ownerBackendUrl()}/webauthn/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Cookie: backendCookie(owner, ceremony) },
    body: await request.text(),
    cache: 'no-store',
  })
  const text = await backend.text()
  const out = new NextResponse(text || null, {
    status: backend.status,
    headers: { 'Content-Type': backend.headers.get('content-type') || 'application/json', 'Cache-Control': 'no-store' },
  })
  clearCeremonyCookie(out)
  return out
}
