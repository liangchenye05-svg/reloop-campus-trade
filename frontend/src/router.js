import { createRouter, createWebHistory } from 'vue-router'
import { session } from './api'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: () => import('./pages/Market.vue') },
    { path: '/login', component: () => import('./pages/Account.vue') },
    { path: '/register', component: () => import('./pages/Account.vue') },
    { path: '/products/:id', component: () => import('./pages/Detail.vue') },
    { path: '/account', component: () => import('./pages/Profile.vue'), meta: { protected: true } },
    { path: '/sell', component: () => import('./pages/Sell.vue'), meta: { protected: true } },
    { path: '/cart', component: () => import('./pages/Cart.vue'), meta: { protected: true } },
    { path: '/orders/:id?', component: () => import('./pages/Orders.vue'), meta: { protected: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})
router.beforeEach(to => to.meta.protected && !session.token ? { path: '/login', query: { next: to.fullPath } } : true)
export default router
