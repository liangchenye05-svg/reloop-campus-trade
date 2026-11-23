<script setup>
import { ref, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Collection, Cpu, Box, Basketball, Goods, Search } from '@element-plus/icons-vue'
import { ElSkeleton, ElSkeletonItem } from 'element-plus'
import { categories, client } from '../api'
import ProductCard from '../components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const icons = { BOOKS: Collection, ELECTRONICS: Cpu, DAILY: Box, SPORTS: Basketball, OTHER: Goods }
const q = ref('')
const category = computed(() => Object.hasOwn(categories, route.query.category) ? route.query.category : '')
const page = computed(() => {
  const value = Number(route.query.page)
  return Number.isInteger(value) && value > 0 && value <= 10000 ? value : 1
})
const data = ref({ items: [], total: 0 })
const busy = ref(true)
const error = ref('')
const refresh = ref(0)

watch([() => route.query, refresh], async (_, previous, onCleanup) => {
  const query = typeof route.query.q === 'string' ? route.query.q.slice(0, 100) : ''
  q.value = query
  const request = new AbortController()
  onCleanup(() => request.abort())
  busy.value = true
  error.value = ''
  try {
    data.value = await client.get('/products', {
      params: { q: query || undefined, category: category.value || undefined, page: page.value },
      signal: request.signal
    })
  } catch (problem) {
    if (!request.signal.aborted) error.value = problem.message
  } finally {
    if (!request.signal.aborted) busy.value = false
  }
}, { immediate: true })

function filter(nextCategory, nextPage = 1) {
  const query = {
    q: q.value.trim() || undefined,
    category: nextCategory || undefined,
    page: nextPage > 1 ? String(nextPage) : undefined
  }
  if (router.resolve({ query }).fullPath === route.fullPath) refresh.value++
  else router.push({ query })
}

function clearFilters() {
  q.value = ''
  filter('')
}
</script>

<template>
  <section class="hero">
    <div class="hero-copy">
      <h1>校园集市</h1>
      <p>淘到刚好需要的好物，也让闲置在校园里继续派上用场。</p>
      <form class="search" role="search" @submit.prevent="filter(category)">
        <el-input size="large" v-model="q" :prefix-icon="Search" placeholder="搜索商品或描述" aria-label="搜索商品或描述" clearable maxlength="100" />
        <el-button native-type="submit" size="large" type="primary" :loading="busy">搜索</el-button>
      </form>
    </div>
    <div class="hero-photo" aria-hidden="true"></div>
  </section>

  <section class="market-section" aria-label="在售闲置">
    <div class="section-head"><h2>在售闲置</h2><span v-if="!busy && !error">{{ data.total }} 件商品</span></div>
    <div class="category-tabs" aria-label="商品分类">
      <button :class="{ selected: !category }" :aria-pressed="!category" @click="filter('')"><el-icon><Goods /></el-icon>全部</button>
      <button v-for="(label, key) in categories" :key="key" :class="{ selected: category === key }" :aria-pressed="category === key" @click="filter(key)">
        <el-icon><component :is="icons[key]" /></el-icon>{{ label }}
      </button>
    </div>
    <template v-if="error">
      <el-alert :title="error" type="error" :closable="false" />
      <el-button class="retry" @click="refresh++">重试</el-button>
    </template>
    <div v-else-if="busy" class="catalog" aria-label="加载商品中" aria-busy="true">
      <ElSkeleton v-for="index in 4" :key="index" animated class="product-card skeleton-card">
        <template #template><ElSkeletonItem variant="image" class="skeleton-image" /><ElSkeletonItem variant="text" /></template>
      </ElSkeleton>
    </div>
    <div v-else-if="data.items.length" class="catalog">
      <ProductCard v-for="product in data.items" :key="product.id" :product="product" />
    </div>
    <el-empty v-else description="暂无符合条件的商品"><el-button @click="clearFilters">清除筛选</el-button></el-empty>
    <el-pagination v-if="data.total > 12 && !error" :current-page="page" :page-size="12" :total="data.total" :disabled="busy" layout="prev, pager, next" @current-change="value => filter(category, value)" />
  </section>
</template>
