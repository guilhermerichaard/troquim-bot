'use client'

function bytesToBase64Url(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function base64UrlToBytes(value: string) {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=')
  const binary = atob(base64)
  return Uint8Array.from(binary, c => c.charCodeAt(0))
}

export function passkeysSupported() {
  return typeof window !== 'undefined' && 'PublicKeyCredential' in window && !!navigator.credentials
}

export async function createPasskey(options: any, label = 'Este dispositivo') {
  const publicKey: PublicKeyCredentialCreationOptions = {
    ...options,
    challenge: base64UrlToBytes(options.challenge),
    user: { ...options.user, id: base64UrlToBytes(options.user.id) },
    excludeCredentials: (options.excludeCredentials || []).map((item: any) => ({
      ...item,
      id: base64UrlToBytes(item.id),
    })),
  }

  const credential = await navigator.credentials.create({ publicKey }) as PublicKeyCredential | null
  if (!credential) throw new Error('Passkey não criada.')
  const response = credential.response as AuthenticatorAttestationResponse

  return {
    publicKey: {
      credential: {
        id: credential.id,
        rawId: bytesToBase64Url(credential.rawId),
        response: {
          attestationObject: bytesToBase64Url(response.attestationObject),
          clientDataJSON: bytesToBase64Url(response.clientDataJSON),
          transports: typeof response.getTransports === 'function' ? response.getTransports() : [],
        },
        type: credential.type,
        clientExtensionResults: credential.getClientExtensionResults(),
        authenticatorAttachment: credential.authenticatorAttachment,
      },
      label,
    },
  }
}

export async function getPasskey(options: any, mediation?: CredentialMediationRequirement) {
  const publicKey: PublicKeyCredentialRequestOptions = {
    ...options,
    challenge: base64UrlToBytes(options.challenge),
    allowCredentials: (options.allowCredentials || []).map((item: any) => ({
      ...item,
      id: base64UrlToBytes(item.id),
    })),
  }

  const credential = await navigator.credentials.get({ publicKey, mediation }) as PublicKeyCredential | null
  if (!credential) throw new Error('Passkey não selecionada.')
  const response = credential.response as AuthenticatorAssertionResponse

  return {
    id: credential.id,
    rawId: bytesToBase64Url(credential.rawId),
    response: {
      authenticatorData: bytesToBase64Url(response.authenticatorData),
      clientDataJSON: bytesToBase64Url(response.clientDataJSON),
      signature: bytesToBase64Url(response.signature),
      userHandle: response.userHandle ? bytesToBase64Url(response.userHandle) : null,
    },
    clientExtensionResults: credential.getClientExtensionResults(),
    authenticatorAttachment: credential.authenticatorAttachment,
    type: credential.type,
  }
}
