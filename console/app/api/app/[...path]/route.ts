import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'
import { ownerBackendUrl, ownerCookieName } from '@/lib/troquim'

async function forward(request: Request, context: { params: Promise<{ path: string[] }> }) {
  const jar = await cookies()
  const token = jar.get(ownerCookieName)?.value
  if (!token) return NextResponse.json({ error: 'Sessão expirada.' }, { status: 401 })

  const { path } = await context.params
  const incoming = new URL(request.url)
  const target = new URL(`${ownerBackendUrl()}/api/v1/app/${path.join('/')}`)
  target.search = incoming.search

  const headers: Record<string,string> = {
    Cookie: `${ownerCookieName}=${token}`,
  }
  const contentType = request.headers.get('content-type')
  if (contentType) headers['Content-Type'] = contentType

  const body = ['GET','HEAD'].includes(request.method) ? undefined : await request.text()
  const response = await fetch(target, {
    method: request.method,
    headers,
    body,
    cache: 'no-store',
  })

  const text = await response.text()
  const out = new NextResponse(text || null, { status: response.status })
  const responseContentType = response.headers.get('content-type')
  if (responseContentType) out.headers.set('content-type', responseContentType)
  return out
}

export const GET = forward
export const POST = forward
export const PUT = forward
export const DELETE = forward
