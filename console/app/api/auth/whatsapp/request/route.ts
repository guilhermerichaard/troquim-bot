import { NextResponse } from 'next/server'
import { ownerBackendUrl } from '@/lib/troquim'
import { rejectCrossOrigin } from '@/lib/csrf'

export async function POST(request: Request) {
  const rejected = rejectCrossOrigin(request)
  if (rejected) return rejected
  const backend = await fetch(`${ownerBackendUrl()}/api/v1/owner/otp/request`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: await request.text(),
    cache: 'no-store',
  })
  const text = await backend.text()
  return new NextResponse(text || null, {
    status: backend.status,
    headers: { 'Content-Type': backend.headers.get('content-type') || 'application/json', 'Cache-Control': 'no-store' },
  })
}
