<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElDrawer, ElTag } from 'element-plus'
import { client, date, money, states } from '../api'

const route = useRoute()
const id = route.params.id
const busy = ref(false)
const error = ref('')
const order = ref(null)
const data = ref({ items: [], total: 0 })
const notices = ref(null)
const noticeOpen = ref(false)
const page = ref(1)
const noticePage = ref(1)
const tones = { PENDING_PAYMENT: 'warning', PAID: 'primary', COMPLETED: 'success', CANCELLED: 'info', EXPIRED: 'info' }

async function run(operation) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await operation() }
  catch (problem) { error.value = problem.message }
  finally { busy.value = false }
}
function load() {
  return run(async () => {
    if (id) order.value = await client.get('/orders/' + id)
    else data.value = await client.get('/orders', { params: { page: page.value } })
  })
}
function change(action) {
  return run(async () => { order.value = await client.post('/orders/' + id + '/' + action) })
}
function loadNotices() {
  noticeOpen.value = true
  return run(async () => { notices.value = await client.get('/orders/notices', { params: { page: noticePage.value } }) })
}
onMounted(load)
</script>

<template>
  <router-link v-if="id" to="/orders" class="back">← 返回订单</router-link>
  <div class="page-head"><h1>{{ id ? '订单详情' : '我的订单' }}</h1>
    <el-button v-if="!id" :disabled="busy" @click="loadNotices">订单通知</el-button>
    <el-button v-else :disabled="busy" @click="load">刷新状态</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <div v-loading="busy" class="orders-space">
    <template v-if="order">
      <div class="receipt-layout">
        <article class="surface panel receipt">
          <header class="receipt-head">
            <div><div class="metadata">订单状态</div><h2>{{ states[order.status] }}</h2></div>
            <ElTag :type="tones[order.status]" effect="light">{{ states[order.status] }}</ElTag>
          </header>
          <dl class="order-meta">
            <dt>订单编号</dt><dd>{{ order.id }}</dd>
            <dt>创建时间</dt><dd>{{ date(order.createdAt) }}</dd>
            <dt v-if="order.status==='PENDING_PAYMENT'">支付截止</dt><dd v-if="order.status==='PENDING_PAYMENT'">{{ date(order.expiresAt) }}</dd>
          </dl>
          <div v-for="item in order.items" :key="item.productId" class="record">
            <strong>{{ item.title }}</strong><span>{{ money(item.unitPrice) }} × {{ item.quantity }}</span>
          </div>
          <div class="receipt-total"><span>订单合计</span><strong>{{ money(order.total) }}</strong></div>
          <div v-if="order.status==='PENDING_PAYMENT'" class="checkout-bar">
            <span class="metadata">模拟支付无需真实付款。</span>
            <div class="controls"><el-button :disabled="busy" @click="change('cancel')">取消订单</el-button><el-button type="primary" :loading="busy" @click="change('pay')">模拟支付</el-button></div>
          </div>
          <div v-else-if="order.status==='PAID'" class="checkout-bar">
            <span class="metadata">线下收到商品后确认交易完成。</span><el-button type="primary" :loading="busy" @click="change('complete')">确认完成</el-button>
          </div>
        </article>
        <aside class="surface panel order-progress">
          <h2>订单进度</h2>
          <ol>
            <li><strong>创建订单</strong><small>{{ date(order.createdAt) }}</small></li>
            <li v-if="order.paidAt"><strong>已支付</strong><small>{{ date(order.paidAt) }}</small></li>
            <li v-if="order.finishedAt"><strong>{{ states[order.status] }}</strong><small>{{ date(order.finishedAt) }}</small></li>
          </ol>
        </aside>
      </div>
    </template>
    <template v-else-if="!id && !error">
      <el-empty v-if="!data.items.length && !busy" description="暂无订单"><router-link to="/" class="back">去集市看看 →</router-link></el-empty>
      <div v-else class="order-cards">
        <router-link v-for="entry in data.items" :key="entry.id" :to="'/orders/' + entry.id" class="surface order-card">
          <ElTag :type="tones[entry.status]" effect="light">{{ states[entry.status] }}</ElTag>
          <strong>{{ money(entry.total) }}</strong>
          <p class="metadata">{{ date(entry.createdAt) }}</p><small>{{ entry.id }}</small>
        </router-link>
      </div>
      <el-pagination v-if="data.total > 12" v-model:current-page="page" :page-size="12" :total="data.total" :disabled="busy" layout="prev, pager, next" @current-change="load" />
    </template>
    <el-button v-if="error && !order" class="retry" :disabled="busy" @click="load">重试</el-button>
  </div>
  <ElDrawer v-model="noticeOpen" title="订单通知" size="min(440px, 90vw)">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="busy" class="orders-space">
      <el-empty v-if="notices && !notices.items.length" description="暂无通知" />
      <div v-for="notice in notices?.items || []" :key="notice.eventId" class="record">
        <router-link :to="'/orders/' + notice.orderId">{{ states[notice.kind] || notice.kind }}</router-link>
        <span class="metadata">{{ date(notice.createdAt) }}</span>
      </div>
      <el-pagination v-if="notices?.total > 12" v-model:current-page="noticePage" :page-size="12" :total="notices.total" :disabled="busy" layout="prev, pager, next" @current-change="loadNotices" />
    </div>
  </ElDrawer>
</template>
