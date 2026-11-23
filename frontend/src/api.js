import { reactive } from 'vue'
import axios from 'axios'

const session = reactive(JSON.parse(sessionStorage.getItem('campus-session') || 'null') || { token: '', user: null })

function setSession(value) {
  session.token = value?.token || ''
  session.user = value?.user || null
  if (value) sessionStorage.setItem('campus-session', JSON.stringify(value))
  else sessionStorage.removeItem('campus-session')
}

const client = axios.create({ baseURL: '/api', timeout: 12000 })
client.interceptors.request.use(config => {
  if (session.token) config.headers.Authorization = 'Bearer ' + session.token
  return config
})
client.interceptors.response.use(response => response.data.data, error => {
  const body = error.response?.data
  if (error.response?.status === 401 && !error.config.url.startsWith('/auth/login')) setSession(null)
  const trace = body?.traceId
  return Promise.reject(new Error((body?.message || '连接失败，请稍后重试') + (trace ? '（请求编号 ' + trace + '）' : '')))
})

export { client, session, setSession }
export const categories = { BOOKS: '教材书籍', ELECTRONICS: '数码电子', DAILY: '生活用品', SPORTS: '运动户外', OTHER: '其他闲置' }
export const states = { PENDING_PAYMENT: '待支付', PAID: '已支付', COMPLETED: '已完成', CANCELLED: '已取消', EXPIRED: '已超时' }
export const money = value => new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value)
export const date = value => value ? new Date(value + (value.endsWith('Z') ? '' : 'Z')).toLocaleString('zh-CN') : '—'
