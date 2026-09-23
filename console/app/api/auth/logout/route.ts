import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { ownerBackendUrl, ownerCookieName } from '@/lib/troquim'

export async function POST() {
  const jar = await cookies()
  const token = jar.get(ownerCookieName)?.value

  if (token) {
    await fetch(`${ownerBackendUrl()}/api/v1/owner/logout`, {
      method: 'POST',
      headers: { Cookie: `${ownerCookieName}=${token}` },
      cache: 'no-store',
    }).catch(() => undefined)
  }

  const response = NextResponse.json({ ok: true })
  response.cookies.delete(ownerCookieName)
  return response
}
