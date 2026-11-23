<script setup>
import { computed, ref, watch } from 'vue'
import { Basketball, Collection, Goods, House, Monitor } from '@element-plus/icons-vue'
const props = defineProps({ url: String, category: String, title: String })
const failed = ref(false)
watch(() => props.url, () => { failed.value = false })
const icon = computed(() => ({ BOOKS: Collection, ELECTRONICS: Monitor, DAILY: House, SPORTS: Basketball })[props.category] || Goods)
</script>

<template>
  <div class="product-image" :data-category="category || 'OTHER'">
    <img v-if="url && !failed" :src="url" :alt="title" referrerpolicy="no-referrer" loading="lazy" @error="failed = true">
    <el-icon v-else aria-hidden="true"><component :is="icon" /></el-icon>
  </div>
</template>
