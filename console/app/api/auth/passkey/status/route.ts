import { NextResponse } from 'next/server'
import { ownerBackendUrl } from '@/lib/troquim'

export async function GET() {
  const backend = await fetch(`${ownerBackendUrl()}/api/v1/owner/passkey/status`, { cache: 'no-store' })
  if (!backend.ok) return NextResponse.json({ enabled: false })
  return NextResponse.json(await backend.json())
}
