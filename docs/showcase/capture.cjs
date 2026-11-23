
const assert = require('node:assert/strict')
const { mkdir, readFile, writeFile } = require('node:fs/promises')
const path = require('node:path')
const { chromium, expect } = require('../../frontend/node_modules/@playwright/test')

const base = 'http://127.0.0.1:8188'
const root = __dirname
const password = process.env.RELOOP_SHOWCASE_PASSWORD
const desktop = { width: 1440, height: 960 }
const mobile = { width: 390, height: 844 }
const errors = []

async function photo(page, format, file) {
  const directory = path.join(root, 'screenshots', format)
  await mkdir(directory, { recursive: true })
  await expect(page.locator('.el-loading-mask')).toHaveCount(0)
  await page.evaluate(async () => { await document.fonts.ready; await new Promise(requestAnimationFrame); await new Promise(requestAnimationFrame) })
  await page.screenshot({ path: path.join(directory, file + '.jpg'), type: 'jpeg', quality: 92 })
}

function watch(page) {
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
}

async function login(page, user, next) {
  await page.goto(base + '/login?next=' + encodeURIComponent(next))
  await page.getByLabel('用户名').fill(user)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(base + next)
}

async function run() {
  assert(password)
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  try {
    const page = await browser.newPage({ viewport: desktop, locale: 'zh-CN' })
    watch(page)
    const catalog = JSON.parse(await readFile(path.join(root, '../../deploy/showcase/catalog.json'), 'utf8'))
    const response = await page.request.get(base + '/api/products?size=100')
    assert.equal(response.status(), 200)
    const { data } = await response.json()
    assert.equal(data.total, catalog.length)
    const product = data.items.find(item => item.imageUrl.endsWith('/keyboard.jpg'))
    assert(product && product.stock > 0)
    for (const item of data.items) {
      const expected = catalog.find(record => record.title === item.title)
      assert(expected)
      assert.equal(Number(item.price), expected.price)
      assert.equal(item.category, expected.category)
      assert.equal(item.status, 'ACTIVE')
      assert(await page.request.get(item.imageUrl).then(result => result.ok()))
    }

    await page.goto(base)
    await expect(page.locator('.product-card')).toHaveCount(10)
    await expect(page.locator('.product-card img').first()).toBeVisible()
    await page.waitForFunction(() => {
      const photos = [...document.querySelectorAll('.product-card img')].slice(0, 4)
      return photos.length === 4 && photos.every(image => image.complete && image.naturalWidth > 0)
    })
    await photo(page, 'desktop', '01-market')
    await page.getByLabel('搜索商品或描述').fill('键盘')
    await page.getByRole('button', { name: '搜索', exact: true }).click()
    await page.getByRole('button', { name: '数码电子', exact: true }).click()
    await expect(page.locator('.product-card')).toHaveCount(1)
    await expect(page.getByRole('link', { name: new RegExp(product.title) })).toBeVisible()
    await photo(page, 'desktop', '02-search')
    await page.getByRole('link', { name: new RegExp(product.title) }).click()
    await expect(page.getByRole('heading', { name: product.title })).toBeVisible()
    await page.waitForFunction(() => [...document.images].some(image => image.src.endsWith('/keyboard.jpg') && image.naturalWidth > 0))
    await photo(page, 'desktop', '03-product')

    await login(page, 'showcase_buyer', '/account')
    await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible()
    await photo(page, 'desktop', '07-profile')
    if (process.argv.includes('--refresh-static')) {
      assert.deepEqual(errors, [])
      console.log('Refreshed four loaded screenshots without modifying demo data.')
      return
    }
    await page.goto(base + '/products/' + product.id)
    await page.getByRole('button', { name: '加入购物车' }).click()
    await expect(page.getByRole('heading', { name: '购物车' })).toBeVisible()
    await expect(page.getByText(product.title, { exact: true })).toBeVisible()
    await page.locator('.el-message').first().waitFor({ state: 'hidden' })
    const titleAndStockAreSeparate = await page.locator('.cart-line .record-title').evaluate(el => {
      const title = el.querySelector('a').getBoundingClientRect()
      const stock = el.querySelector('.metadata').getBoundingClientRect()
      return title.height > 10 && stock.top >= title.bottom - 1
    })
    assert(titleAndStockAreSeparate)
    await photo(page, 'desktop', '04-cart')
    await page.getByRole('button', { name: '创建订单' }).click()
    await expect(page.getByRole('heading', { name: '待支付' })).toBeVisible()
    await page.getByRole('button', { name: '模拟支付' }).click()
    await expect(page.getByRole('heading', { name: '已支付' })).toBeVisible()
    await page.getByRole('button', { name: '确认完成' }).click()
    await expect(page.getByRole('heading', { name: '已完成' })).toBeVisible()
    await photo(page, 'desktop', '05-order')
    const remaining = await page.request.get(base + '/api/products/' + product.id).then(response => response.json())
    assert.equal(remaining.data.stock, product.stock - 1)

    const seller = await browser.newPage({ viewport: desktop, locale: 'zh-CN' })
    watch(seller)
    await login(seller, 'showcase_seller', '/sell')
    await expect(seller.getByRole('heading', { name: '发布与管理' })).toBeVisible()
    await expect(seller.getByText(product.title, { exact: true })).toBeVisible()
    await seller.getByLabel('商品名称').fill('校园自习桌垫')
    await seller.getByLabel('商品描述').fill('边角完整，适合桌面书写，校内公共区面交。')
    await photo(seller, 'desktop', '06-publish')

    const phone = await browser.newPage({ viewport: mobile, locale: 'zh-CN', isMobile: true, hasTouch: true })
    watch(phone)
    await phone.goto(base)
    await expect(phone.locator('.product-card')).toHaveCount(10)
    await phone.waitForFunction(() => [...document.images].some(image => image.src.endsWith('/textbooks.jpg') && image.naturalWidth > 0))
    assert(await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await photo(phone, 'mobile', '01-market')

    assert.deepEqual(errors, [])
    const result = { browser: browser.version(), source: base, desktop, mobile, catalog: data.total,
      imageResponses: 10, paymentAndCompletion: true, stockBefore: product.stock,
      stockAfter: remaining.data.stock, screenshots: 8, consoleErrors: 0 }
    await mkdir(path.join(root, 'local-evidence'), { recursive: true })
    await writeFile(path.join(root, 'local-evidence/browser.json'), JSON.stringify(result, null, 2) + '\n')
    console.log(JSON.stringify(result))
  } finally {
    await browser.close()
  }
}

run().catch(error => { console.error(error.stack); process.exitCode = 1 })
