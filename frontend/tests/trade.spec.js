import { test, expect } from '@playwright/test'
import { randomBytes } from 'node:crypto'

test('seller publishes and buyer completes a simulated trade', async ({ browser, baseURL }) => {
  const nonce = randomBytes(5).toString('hex')
  const title = '二手线性代数课本 ' + nonce
  const password = randomBytes(18).toString('base64url')
  const seller = await browser.newPage({ baseURL })
  const buyer = await browser.newPage({ baseURL, viewport: { width: 390, height: 844 } })
  const errors = []
  for (const page of [seller, buyer]) page.on('pageerror', error => errors.push(error.message))

  async function register(page, role) {
    await page.goto('/register')
    await page.getByLabel('用户名').fill(role + '_' + nonce)
    await page.getByLabel('昵称').fill(role)
    await page.getByLabel('密码', { exact: true }).fill(password)
    await page.getByRole('button', { name: '注册并登录' }).click()
    await expect(page.getByRole('heading', { name: '校园集市', exact: true })).toBeVisible()
  }

  await register(seller, 'seller')
  await seller.getByRole('link', { name: '发布闲置' }).click()
  await seller.getByLabel('商品名称').fill(title)
  await seller.getByLabel('商品描述').fill('适合大一，校内面交')
  await seller.getByRole('button', { name: '确认发布' }).click()
  const record = seller.locator('.record').filter({ hasText: title })
  await expect(record).toBeVisible()
  await record.getByRole('button', { name: '下架', exact: true }).click()
  await expect(record.getByText('已下架', { exact: false })).toBeVisible()
  await record.getByRole('button', { name: '上架', exact: true }).click()
  await expect(record.getByText('在售', { exact: false })).toBeVisible()
  await record.getByRole('button', { name: '编辑' }).click()
  await seller.getByLabel('商品描述').fill('校内图书馆面交')
  await seller.getByRole('button', { name: '保存修改' }).click()
  await expect(seller.getByRole('button', { name: '确认发布' })).toBeVisible()

  await register(buyer, 'buyer')
  await buyer.getByLabel('搜索商品或描述').fill(nonce)
  await buyer.getByRole('button', { name: '搜索' }).click()
  await buyer.getByRole('link', { name: new RegExp(title) }).click()
  await expect(buyer.getByText('校内图书馆面交')).toBeVisible()
  await buyer.getByRole('button', { name: '加入购物车' }).click()
  await expect(buyer.getByRole('heading', { name: '购物车' })).toBeVisible()
  await buyer.getByRole('button', { name: '创建订单' }).click()
  await expect(buyer.getByRole('heading', { name: '待支付' })).toBeVisible()
  await buyer.getByRole('button', { name: '模拟支付' }).click()
  await expect(buyer.getByRole('heading', { name: '已支付' })).toBeVisible()
  await buyer.getByRole('button', { name: '确认完成' }).click()
  await expect(buyer.getByRole('heading', { name: '已完成' })).toBeVisible()
  await buyer.reload()
  await expect(buyer.getByRole('heading', { name: '已完成' })).toBeVisible()
  expect(await buyer.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  expect(errors).toEqual([])
  await seller.close()
  await buyer.close()
})
