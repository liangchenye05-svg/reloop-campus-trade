<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { categories, client, money } from '../api'
import ProductCard from '../components/ProductCard.vue'
const blank = () => ({ title: '', category: 'BOOKS', description: '', price: 1, stock: 1, imageUrl: '' })
const form = ref(blank())
const editing = ref(null)
const errors = ref('')
const busy = ref(false)
const page = ref(1)
const data = ref({ items: [], total: 0 })
async function load() {
  try { data.value = await client.get('/products/mine', { params: { page: page.value } }) }
  catch (problem) { errors.value = problem.message }
}
onMounted(load)
function edit(product) {
  editing.value = product.id
  form.value = { title: product.title, category: product.category, description: product.description,
    price: product.price, stock: product.stock, imageUrl: product.imageUrl || '' }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
function reset() {
  editing.value = null
  form.value = blank()
}
async function save() {
  if (busy.value) return
  errors.value = ''
  busy.value = true
  const body = { ...form.value, imageUrl: form.value.imageUrl || null }
  try {
    if (editing.value) await client.put('/products/' + editing.value, body)
    else await client.post('/products', body)
    ElMessage.success(editing.value ? '修改已保存' : '商品已发布')
    reset()
    await load()
  } catch (problem) { errors.value = problem.message }
  finally { busy.value = false }
}
async function toggle(product) {
  if (busy.value) return
  busy.value = true
  errors.value = ''
  try {
    await client.patch('/products/' + product.id + '/status', { status: product.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' })
    await load()
  } catch (problem) { errors.value = problem.message }
  finally { busy.value = false }
}
</script>

<template>
  <div class="page-head"><h1>发布与管理</h1></div>
  <el-alert v-if="errors" :title="errors" type="error" :closable="false" />
  <div class="receipt-layout sell-layout">
  <section class="surface panel">
    <h2>{{ editing ? '编辑商品' : '发布闲置' }}</h2>
    <form class="form-stack" @submit.prevent="save">
      <el-form-item label="商品名称" label-position="top"><el-input v-model.trim="form.title" aria-label="商品名称" required maxlength="100" /></el-form-item>
      <div class="fields">
        <el-form-item label="分类" label-position="top"><el-select v-model="form.category" aria-label="分类"><el-option v-for="(label, key) in categories" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item label="价格（元）" label-position="top"><el-input-number v-model="form.price" aria-label="价格（元）" :precision="2" :step="0.01" :min="0.01" :max="9999999999.99" /></el-form-item>
        <el-form-item label="可售库存" label-position="top"><el-input-number v-model="form.stock" aria-label="可售库存" :precision="0" :min="0" :max="100000" /></el-form-item>
      </div>
      <el-form-item label="商品描述" label-position="top"><el-input v-model.trim="form.description" type="textarea" aria-label="商品描述" :rows="4" required maxlength="2000" /></el-form-item>
      <el-form-item label="图片链接（可选）" label-position="top"><el-input v-model.trim="form.imageUrl" aria-label="图片链接（可选）" type="url" maxlength="600" /></el-form-item>
      <div class="controls"><el-button native-type="submit" type="primary" :loading="busy">{{ editing ? '保存修改' : '确认发布' }}</el-button><el-button v-if="editing" @click="reset">取消编辑</el-button></div>
    </form>
  </section>
  <aside class="surface panel"><h2>商品卡预览</h2><ProductCard :product="form" preview /></aside>
  </div>
  <section class="surface panel">
    <div class="section-head"><h2>我的商品</h2><span>{{ data.total }} 件</span></div>
    <el-empty v-if="!data.items.length" description="还没有发布商品" :image-size="70" />
    <div v-for="product in data.items" :key="product.id" class="record">
      <div><strong>{{ product.title }}</strong><div class="metadata">{{ money(product.price) }} · 库存 {{ product.stock }} · {{ product.status === 'ACTIVE' ? '在售' : '已下架' }}</div></div>
      <div class="controls"><el-button :disabled="busy" @click="edit(product)">编辑</el-button><el-button :disabled="busy" @click="toggle(product)">{{ product.status === 'ACTIVE' ? '下架' : '上架' }}</el-button></div>
    </div>
    <el-pagination v-if="data.total > 12" v-model:current-page="page" :page-size="12" :total="data.total" layout="prev, pager, next" @current-change="load" />
  </section>
</template>
