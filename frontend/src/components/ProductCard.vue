<script setup>
import { useRoute } from 'vue-router'
import { TopRight } from '@element-plus/icons-vue'
import { categories, money } from '../api'
import ProductImage from './ProductImage.vue'

const route = useRoute()
defineProps({ product: { type: Object, required: true }, preview: Boolean })
</script>

<template>
  <component :is="preview ? 'article' : 'router-link'" :to="preview ? undefined : {path:'/products/' + product.id, query:route.query}" class="product-card">
    <ProductImage :url="product.imageUrl" :category="product.category" :title="product.title || '商品预览'" />
    <div class="product-copy">
      <div class="product-meta"><span>{{ categories[product.category] }}</span><span :class="{ 'stock-empty': !product.stock }">{{ product.stock ? '库存 ' + product.stock : '已售罄' }}</span></div>
      <h3>{{ product.title || '商品名称' }}</h3>
      <div class="product-bottom"><strong>{{ money(product.price) }}</strong><el-icon v-if="!preview" aria-hidden="true"><TopRight /></el-icon></div>
    </div>
  </component>
</template>
