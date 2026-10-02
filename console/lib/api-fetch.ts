import { consolePath } from '@/lib/base-path'

export function apiFetch(input: RequestInfo | URL, init?: RequestInit) {
  if (typeof input === 'string' && input.startsWith('/api/')) {
    return fetch(consolePath(input), init)
  }
  return fetch(input, init)
}
