import { NextResponse } from 'next/server'
import { ownerCookieName } from '@/lib/troquim'

export const ceremonyCookieName = 'troquim_webauthn_ceremony'

export function extractCookie(setCookie: string | null, name: string) {
  if (!setCookie) return null
  const match = setCookie.match(new RegExp(`(?:^|,|\\s)${name}=([^;]+)`))
  return match?.[1] || null
}

export function backendCookie(owner?: string, ceremony?: string) {
  return [
    owner ? `${ownerCookieName}=${owner}` : '',
    ceremony ? `JSESSIONID=${ceremony}` : '',
  ].filter(Boolean).join('; ')
}

export function attachCeremonyCookie(out: NextResponse, backend: Response) {
  const value = extractCookie(backend.headers.get('set-cookie'), 'JSESSIONID')
  if (!value) return
  out.cookies.set(ceremonyCookieName, value, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    path: '/',
    maxAge: 5 * 60,
  })
}

export function clearCeremonyCookie(out: NextResponse) {
  out.cookies.set(ceremonyCookieName, '', {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    path: '/',
    maxAge: 0,
  })
}

export function attachOwnerCookie(out: NextResponse, backend: Response) {
  const value = extractCookie(backend.headers.get('set-cookie'), ownerCookieName)
  if (!value) return false
  out.cookies.set(ownerCookieName, value, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    path: '/',
    maxAge: 12 * 60 * 60,
  })
  return true
}
