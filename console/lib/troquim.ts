import { cookies } from 'next/headers'

const COOKIE = 'troquim_owner_session'

function backendUrl() {
  const url = process.env.TROQUIM_BACKEND_URL?.replace(/\/$/, '')
  if (!url) throw new Error('TROQUIM_BACKEND_URL não configurada')
  return url
}

export async function troquimFetch<T>(path: string): Promise<T | null> {
  const jar = await cookies()
  const token = jar.get(COOKIE)?.value
  if (!token) return null

  const response = await fetch(`${backendUrl()}/api/v1/app${path}`, {
    headers: { Cookie: `${COOKIE}=${token}` },
    cache: 'no-store',
  })

  if (response.status === 401 || response.status === 403) return null
  if (!response.ok) throw new Error(`Troquim API falhou: ${response.status}`)
  return response.json() as Promise<T>
}

export const ownerCookieName = COOKIE
export const ownerBackendUrl = backendUrl
