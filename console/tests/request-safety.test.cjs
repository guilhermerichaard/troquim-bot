const { test } = require('node:test')
const assert = require('node:assert/strict')
const { isSameOriginMutation, isOwnerPath } = require('../.test-build/request-safety.js')
const { createCommandKeys, slotsQuery } = require('../.test-build/booking-request.js')

const canonical = 'https://app.troquim.app'
const request = headers => new Request(`${canonical}/api/auth/login`, { method: 'POST', headers })

test('accepts the canonical origin and rejects sibling, hostile, null and absent origins', () => {
  assert.equal(isSameOriginMutation(request({ origin: canonical }), canonical), true)
  for (const origin of ['https://other.troquim.app', 'https://evil.example', 'null', `${canonical}.evil.example`]) {
    assert.equal(isSameOriginMutation(request({ origin }), canonical), false)
  }
  assert.equal(isSameOriginMutation(request({}), canonical), false)
})
test('Fetch Metadata and configured origin cannot be bypassed through request host', () => {
  assert.equal(isSameOriginMutation(request({ origin: canonical, 'sec-fetch-site': 'cross-site' }), canonical), false)
  const spoof = new Request('https://evil.example/api/auth/login', { headers: { origin: 'https://evil.example' } })
  assert.equal(isSameOriginMutation(spoof, canonical), false)
  assert.equal(isSameOriginMutation(request({ origin: canonical, 'sec-fetch-site': 'same-origin' })), true)
})
test('owner proxy rejects traversal, encoded separators and empty paths', () => {
  for (const path of [[], ['..'], ['%2e%2e'], ['appointments/cancel'], ['a\\b'], ['a?b']]) {
    assert.equal(isOwnerPath(path), false)
  }
  assert.equal(isOwnerPath(['appointments', '550e8400-e29b-41d4-a716-446655440000', 'cancel']), true)
})
test('uncertain booking retries preserve idempotency; changing date creates a new command', () => {
  let generated = 0
  const key = createCommandKeys(() => `key-${++generated}`)
  const payload = { customerId: 'customer', date: '2026-09-28', time: '09:00' }
  assert.equal(key(payload), key({ ...payload }))
  assert.equal(generated, 1)
  assert.notEqual(key(payload), key({ ...payload, date: '2026-09-29' }))
})
test('availability identity changes whenever service, professional or date changes', () => {
  const original = slotsQuery('service', 'professional', '2026-09-28')
  assert.notEqual(original, slotsQuery('other', 'professional', '2026-09-28'))
  assert.notEqual(original, slotsQuery('service', 'other', '2026-09-28'))
  assert.notEqual(original, slotsQuery('service', 'professional', '2026-09-29'))
  assert.equal(slotsQuery('service', '', '2026-09-28'), '')
})
