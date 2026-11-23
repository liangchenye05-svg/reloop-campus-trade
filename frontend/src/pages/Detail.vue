<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { categories, client, money, session } from '../api'
import ProductImage from '../components/ProductImage.vue'
const route = useRoute()
const router = useRouter()
const product = ref(null)
const error = ref('')
const busy = ref(true)
const quantity = ref(1)
onMounted(async () => {
  try { product.value = await client.get('/products/' + route.params.id) }
  catch (problem) { error.value = problem.message }
  finally { busy.value = false }
})
async function add() {
  if (!session.token) return router.push({ path: '/login', query: { next: route.fullPath } })
  busy.value = true
  try {
    await client.put('/cart/' + product.value.id, { quantity: quantity.value })
    ElMessage.success('购物车已更新')
    await router.push('/cart')
  } catch (problem) { error.value = problem.message }
  finally { busy.value = false }
}
async function moderate() {
  try {
    await client.patch('/products/' + product.value.id + '/status', { status: 'INACTIVE' })
    await router.push('/')
  } catch (problem) { error.value = problem.message }
}
</script>

<template>
  <router-link :to="{path:'/',query:route.query}" class="back">← 返回集市</router-link>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <div v-loading="busy" class="detail-grid surface">
    <template v-if="product">
      <ProductImage :url="product.imageUrl" :category="product.category" :title="product.title" />
      <section class="detail-copy">
        <span class="metadata">{{ categories[product.category] }}</span>
        <h1>{{ product.title }}</h1>
        <strong class="detail-price">{{ money(product.price) }}</strong>
        <p class="description">{{ product.description }}</p>
        <p v-if="product.seller" class="metadata">发布人 {{ product.seller.displayName }}</p>
        <p :class="{'stock-empty': !product.stock}">{{ product.stock ? '可售库存 ' + product.stock : '暂时售罄' }}</p>
        <router-link v-if="session.user?.id === product.sellerId" to="/sell">管理此商品</router-link>
        <div v-else-if="product.stock" class="controls purchase-bar">
          <el-input-number v-model="quantity" aria-label="购买数量" :min="1" :max="Math.min(99, product.stock)" />
          <el-button type="primary" :loading="busy" :disabled="!quantity" @click="add">加入购物车</el-button>
        </div>
        <el-button v-if="session.user?.role === 'ADMIN' && session.user.id !== product.sellerId" @click="moderate">下架商品</el-button>
      </section>
    </template>
  </div>
</template>
