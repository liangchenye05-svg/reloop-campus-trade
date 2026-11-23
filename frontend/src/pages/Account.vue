<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { client, session, setSession } from '../api'
const route = useRoute()
const router = useRouter()
const registration = route.path === '/register'
const form = ref({ username: '', displayName: '', password: '' })
const busy = ref(false)
const error = ref('')
async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  const { username, displayName, password } = form.value
  try {
    if (registration) await client.post('/auth/register', { username, displayName, password })
    setSession(await client.post('/auth/login', { username, password }))
    const next = route.query.next
    await router.push(typeof next === 'string' && /^\/(?![\\/])/.test(next) ? next : '/')
  } catch (problem) { error.value = problem.message }
  finally { busy.value = false }
}
</script>

<template>
  <section class="account-layout surface">
    <div class="account-side"><h1>{{ registration ? '注册账号' : '登录集市' }}</h1><p>登录即可发布闲置、加入购物车和管理订单。</p></div>
    <form class="form-stack" @submit.prevent="submit">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-form-item label="用户名" label-position="top">
        <el-input v-model.trim="form.username" aria-label="用户名" required minlength="3" maxlength="32" autocomplete="username" placeholder="3–32 位字母、数字或下划线" />
      </el-form-item>
      <el-form-item v-if="registration" label="昵称" label-position="top">
        <el-input v-model.trim="form.displayName" aria-label="昵称" required maxlength="40" autocomplete="nickname" />
      </el-form-item>
      <el-form-item label="密码" label-position="top">
        <el-input v-model="form.password" aria-label="密码" type="password" required minlength="8" maxlength="72" :autocomplete="registration ? 'new-password' : 'current-password'" show-password />
      </el-form-item>
      <el-button native-type="submit" type="primary" :loading="busy">{{ registration ? '注册并登录' : '登录' }}</el-button>
      <router-link :to="{ path: registration ? '/login' : '/register', query: route.query }">{{ registration ? '已有账号，去登录' : '新用户注册' }}</router-link>
      <span v-if="session.user" class="metadata">当前账号 {{ session.user.displayName }}</span>
    </form>
  </section>
</template>
