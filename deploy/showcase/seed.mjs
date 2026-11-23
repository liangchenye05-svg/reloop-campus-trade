
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const base = 'http://127.0.0.1:8188'
const catalog = JSON.parse(await readFile(new URL('./catalog.json', import.meta.url), 'utf8'))
const password = process.env.RELOOP_SHOWCASE_PASSWORD
assert(password, 'Set RELOOP_SHOWCASE_PASSWORD for the dedicated demo accounts.')

async function request(path, { method = 'GET', token, body } = {}) {
  const response = await fetch(base + '/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(12000)
  })
  const envelope = await response.json()
  return { status: response.status, data: envelope.data }
}

const before = await request('/products?size=100')
assert.equal(before.status, 200, 'Start the isolated reloop-showcase project on port 8188 first.')
assert(before.data.total === before.data.items.length, 'Unexpected data at the isolated demo endpoint.')
const titles = new Set(catalog.map(product => product.title))
assert(before.data.items.every(item => titles.has(item.title)), 'The catalog contains non-showcase data.')

async function prepareAccount(username, displayName) {
  const credentials = { username, password }
  let session = await request('/auth/login', { method: 'POST', body: credentials })
  if (session.status === 401) {
    const registration = await request('/auth/register', { method: 'POST', body: { ...credentials, displayName } })
    assert.equal(registration.status, 200, 'Showcase account registration failed for ' + username)
    session = await request('/auth/login', { method: 'POST', body: credentials })
  }
  assert.equal(session.status, 200, 'Showcase account login failed for ' + username)
  return session.data.token
}

const seller = await prepareAccount('showcase_seller', '校园闲置整理员')
await prepareAccount('showcase_buyer', '校园好物买家')
const current = await request('/products/mine?size=100', { token: seller })
assert.equal(current.status, 200)
assert(current.data.items.every(item => titles.has(item.title)), 'The demo seller owns unexpected products.')
for (const { image, ...item } of catalog) {
  const imageUrl = base + '/showcase-products/' + image + '.jpg'
  const imageResponse = await fetch(imageUrl, { method: 'HEAD' })
  assert.equal(imageResponse.status, 200, 'Missing demo image: ' + image)
  assert.match(imageResponse.headers.get('content-type'), /^image\/jpeg/)
  if (current.data.items.some(existing => existing.title === item.title)) continue
  const result = await request('/products', { method: 'POST', token: seller, body: { ...item, imageUrl } })
  assert.equal(result.status, 200, 'Product publication failed: ' + item.title)
}
const after = await request('/products?size=100')
assert.equal(after.status, 200)
assert.equal(after.data.total, catalog.length)
for (const item of catalog) {
  const api = after.data.items.find(product => product.title === item.title)
  assert(api && api.imageUrl.endsWith('/' + item.image + '.jpg'))
  assert.equal(api.category, item.category)
  assert.equal(Number(api.price), item.price)
  assert.equal(api.status, 'ACTIVE')
}
console.log(JSON.stringify({ fixture: 'reloop-showcase-v1', catalog: after.data.total, backendMatches: true }))
