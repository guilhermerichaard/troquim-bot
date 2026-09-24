import { notFound } from 'next/navigation'
import { ownerBackendUrl } from '@/lib/troquim'
import { BookingClient, type PublicBusiness } from './booking-client'

export const dynamic = 'force-dynamic'

export default async function PublicBookingPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const response = await fetch(
    `${ownerBackendUrl()}/api/v1/public/businesses/${encodeURIComponent(slug)}`,
    { cache: 'no-store' }
  )
  if (response.status === 404) notFound()
  if (!response.ok) {
    return <main className="publicBookingShell"><div className="publicBookingCard">
      <h1>Agenda indisponível</h1>
      <p>Tente novamente em instantes.</p>
    </div></main>
  }
  const business = await response.json() as PublicBusiness
  return <BookingClient business={business}/>
}
