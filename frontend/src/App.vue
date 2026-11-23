<script setup>
import { ArrowRight, RefreshRight, ShoppingCart, Tickets } from '@element-plus/icons-vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useRoute } from 'vue-router'
import { session } from './api'

const route = useRoute()
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <header class="topbar">
      <div class="shell nav-inner">
        <router-link to="/" class="brand">
          <span class="brand-icon"><el-icon><RefreshRight /></el-icon></span>ReLoop
        </router-link>
        <nav aria-label="主导航">
          <router-link to="/" exact-active-class="nav-active">逛集市</router-link>
          <router-link to="/cart"><el-icon><ShoppingCart /></el-icon><span>购物车</span></router-link>
          <router-link to="/orders"><el-icon><Tickets /></el-icon><span>我的订单</span></router-link>
        </nav>
        <div class="nav-actions">
          <router-link v-if="session.user" to="/account" class="plain account" :title="session.user.displayName">个人中心</router-link>
          <router-link v-else to="/login" class="plain">登录</router-link>
          <router-link to="/sell" class="action">发布闲置 <el-icon><ArrowRight /></el-icon></router-link>
        </div>
      </div>
    </header>
    <main class="shell content"><router-view :key="route.path" /></main>
    <footer class="shell footer"><strong>ReLoop</strong><span>模拟支付 · 校内线下交付</span></footer>
  </ElConfigProvider>
</template>
