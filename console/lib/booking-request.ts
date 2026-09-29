/** Keep the command key when retrying the same operation after an uncertain response. */
export function createCommandKeys(newKey: () => string = () => crypto.randomUUID()) {
  let previousPayload = ''
  let key = ''
  return (payload: object): string => {
    const fingerprint = JSON.stringify(payload)
    if (fingerprint !== previousPayload) {
      previousPayload = fingerprint
      key = newKey()
    }
    return key
  }
}

export function slotsQuery(serviceId: string, professionalId: string, date: string) {
  return serviceId && professionalId && date
    ? new URLSearchParams({ serviceId, professionalId, date }).toString() : ''
}
