import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { ownerBackendUrl, ownerCookieName } from '@/lib/troquim'
import { rejectCrossOrigin } from '@/lib/csrf'

export async function POST(request: Request) {
  const rejected = rejectCrossOrigin(request)
  if (rejected) return rejected
  const jar = await cookies()
  const token = jar.get(ownerCookieName)?.value

  if (token) {
    const backend = await fetch(`${ownerBackendUrl()}/api/v1/owner/logout`, {
      method: 'POST',
      headers: { Cookie: `${ownerCookieName}=${token}` },
      cache: 'no-store',
      signal: AbortSignal.timeout(10000),
    }).catch(() => null)
    if (!backend?.ok) {
      return NextResponse.json({ error: 'Não foi possível encerrar a sessão. Tente novamente.' }, { status: 502 })
    }
  }

  const response = NextResponse.json({ ok: true })
  response.cookies.delete(ownerCookieName)
  return response
}
