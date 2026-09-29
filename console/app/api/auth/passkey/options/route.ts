import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { ownerBackendUrl } from '@/lib/troquim'
import { rejectCrossOrigin } from '@/lib/csrf'
import { attachCeremonyCookie, ceremonyCookieName } from '@/lib/passkey-proxy'

export async function POST(request: Request) {
  const rejected = rejectCrossOrigin(request)
  if (rejected) return rejected
  const backend = await fetch(`${ownerBackendUrl()}/webauthn/authenticate/options`, {
    method: 'POST',
    cache: 'no-store',
  })
  const text = await backend.text()
  const out = new NextResponse(text || null, {
    status: backend.status,
    headers: { 'Content-Type': backend.headers.get('content-type') || 'application/json', 'Cache-Control': 'no-store' },
  })
  attachCeremonyCookie(out, backend)
  if (!backend.ok) {
    const jar = await cookies()
    if (jar.get(ceremonyCookieName)) out.cookies.delete(ceremonyCookieName)
  }
  return out
}
