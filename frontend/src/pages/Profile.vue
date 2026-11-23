<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Box, ShoppingCart, Tickets } from '@element-plus/icons-vue'
import { client, session, setSession } from '../api'

const router = useRouter()
const route = useRoute()
const person = ref(session.user)
const error = ref('')
const busy = ref(false)
const shortcuts = [
  { to: '/cart', label: '购物车', copy: '查看已选闲置', icon: ShoppingCart },
  { to: '/orders', label: '我的订单', copy: '处理订单与通知', icon: Tickets },
  { to: '/sell', label: '我的商品', copy: '发布或管理闲置', icon: Box }
]

async function load() {
  busy.value = true
  error.value = ''
  try { person.value = await client.get('/auth/me') }
  catch (problem) {
    if (!session.token) await router.replace({ path: '/login', query: { next: route.fullPath } })
    else error.value = problem.message
  } finally { busy.value = false }
}

function logout() {
  setSession(null)
  router.push('/')
}

onMounted(load)
</script>

<template>
  <div class="page-head"><h1>个人中心</h1></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <div v-loading="busy" class="profile-layout">
    <section v-if="person" class="surface panel profile-info">
      <div class="profile-avatar" aria-hidden="true">{{ person.displayName.slice(0, 1) }}</div>
      <div><h2>{{ person.displayName }}</h2><p class="metadata">@{{ person.username }} · {{ person.role === 'ADMIN' ? '管理员' : '校园用户' }}</p></div>
      <el-button @click="logout">退出登录</el-button>
    </section>
    <el-button v-if="error" class="retry" @click="load">重试</el-button>
    <div class="profile-links">
      <router-link v-for="item in shortcuts" :key="item.to" :to="item.to" class="surface panel">
        <el-icon aria-hidden="true"><component :is="item.icon" /></el-icon>
        <h2>{{ item.label }}</h2><p class="metadata">{{ item.copy }}</p>
      </router-link>
    </div>
  </div>
</template>
