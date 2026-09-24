import { NextResponse } from 'next/server'
import { ownerBackendUrl } from '@/lib/troquim'

async function forward(request: Request, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params
  const incoming = new URL(request.url)
  const target = new URL(`${ownerBackendUrl()}/api/v1/public/${path.join('/')}`)
  target.search = incoming.search

  const headers: Record<string,string> = {}
  const contentType = request.headers.get('content-type')
  const idempotencyKey = request.headers.get('idempotency-key')
  if (contentType) headers['Content-Type'] = contentType
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey

  const body = ['GET','HEAD'].includes(request.method) ? undefined : await request.text()
  try {
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
  } catch {
    return NextResponse.json(
      { code: 'BACKEND_UNAVAILABLE', message: 'Agendamento temporariamente indisponível.' },
      { status: 503 }
    )
  }
}

export const GET = forward
export const POST = forward
