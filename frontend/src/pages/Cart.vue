<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { client, money } from '../api'
const router = useRouter()
const rows = ref([])
const selected = ref([])
const busy = ref(false)
const error = ref('')
let attempted = { signature: '', key: '' }
const available = row => row.status === 'ACTIVE' && row.stock >= row.quantity
const eligible = computed(() => rows.value.filter(available).map(row => row.productId))
const allSelected = computed(() => eligible.value.length > 0 && selected.value.length === eligible.value.length)
function selectAll(checked) {
  selected.value = checked ? [...eligible.value] : []
}
const total = computed(() => rows.value.filter(row => selected.value.includes(row.productId))
  .reduce((sum, row) => sum + Math.round(row.price * 100) * row.quantity, 0) / 100)
async function load(first = false) {
  rows.value = await client.get('/cart')
  const eligible = rows.value.filter(available).map(row => row.productId)
  selected.value = first ? eligible : selected.value.filter(id => eligible.includes(id))
}
async function run(operation) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await operation() }
  catch (problem) { error.value = problem.message }
  finally { busy.value = false }
}
onMounted(() => run(() => load(true)))
function update(row, quantity) {
  if (!quantity || quantity === row.quantity) return
  return run(async () => {
    await client.put('/cart/' + row.productId, { quantity })
    await load()
  })
}
function remove(row) {
  return run(async () => {
    await client.delete('/cart/' + row.productId)
    await load()
  })
}
function checkout() {
  return run(async () => {
    const ids = [...selected.value].sort((a, b) => a - b)
    const signature = ids.join(',')
    if (attempted.signature !== signature) {
      const bytes = crypto.getRandomValues(new Uint8Array(16))
      const key = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
      attempted = { signature, key }
    }
    const order = await client.post('/orders', { productIds: ids }, { headers: { 'Idempotency-Key': attempted.key } })
    await router.push('/orders/' + order.id)
  })
}
</script>

<template>
  <div class="page-head"><h1>购物车</h1><router-link to="/">继续逛集市 →</router-link></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <el-button v-if="error" class="retry" :disabled="busy" @click="run(() => load(!rows.length))">刷新购物车</el-button>
  <div v-loading="busy" class="receipt-layout cart-layout">
    <section class="surface panel">
      <el-empty v-if="!rows.length && !busy" description="购物车是空的" />
      <template v-else-if="rows.length">
        <el-checkbox :model-value="allSelected" :indeterminate="selected.length>0 && !allSelected" :disabled="busy || !eligible.length" @change="selectAll">全选可结算商品</el-checkbox>
        <el-checkbox-group v-model="selected" :disabled="busy">
          <div v-for="row in rows" :key="row.productId" class="record cart-line">
            <el-checkbox :value="row.productId" :disabled="!available(row)" :aria-label="'选择 ' + row.title" />
            <div class="record-title">
              <router-link :to="'/products/' + row.productId">{{ row.title }}</router-link>
              <div class="metadata">{{ money(row.price) }} · {{ row.status === 'ACTIVE' ? '库存 ' + row.stock : '已下架' }}</div>
              <div v-if="row.status === 'ACTIVE' && !available(row)" class="metadata stock-empty">当前库存不足</div>
            </div>
            <el-input-number v-if="row.status === 'ACTIVE' && row.stock" :model-value="row.quantity" :min="1" :max="Math.min(row.stock, 99)" :disabled="busy" :aria-label="row.title + ' 数量'" @change="value => update(row, value)" />
            <el-button :disabled="busy" @click="remove(row)">移除</el-button>
          </div>
        </el-checkbox-group>
      </template>
    </section>
    <aside v-if="rows.length" class="surface panel cart-summary">
      <h2>结算摘要</h2>
      <p class="metadata">已选 {{ selected.length }} 项</p>
      <div class="receipt-total"><span>合计</span><strong>{{ money(total) }}</strong></div>
      <el-button type="primary" :disabled="!selected.length || selected.length > 50" :loading="busy" @click="checkout">创建订单</el-button>
      <p v-if="selected.length > 50" class="metadata">每次最多结算 50 项商品。</p>
    </aside>
  </div>
</template>
