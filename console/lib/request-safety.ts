/** Only the configured UI origin may mutate the cookie-authenticated BFF. */
export function isSameOriginMutation(request: Request, configuredOrigin?: string): boolean {
  const origin = request.headers.get('origin')
  if (!origin || origin === 'null') return false
  const site = request.headers.get('sec-fetch-site')
  if (site && site !== 'same-origin') return false
  try {
    const expected = new URL(configuredOrigin || request.url).origin
    return new URL(origin).origin === expected && origin === expected
  } catch {
    return false
  }
}

/** Restrict the proxy to owner operations, never accept path traversal. */
export function isOwnerPath(path: string[]): boolean {
  return path.length > 0 && path.every(segment => /^[a-zA-Z0-9_-]+$/.test(segment))
}
