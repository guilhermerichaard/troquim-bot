export const consoleBasePath = process.env.NEXT_PUBLIC_TROQUIM_CONSOLE_BASE_PATH || ''

export function consolePath(path: string) {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${consoleBasePath}${normalized}`
}
