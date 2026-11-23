import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const proxy = { '/api': process.env.CAMPUS_API_TARGET || 'http://127.0.0.1:8080' }
export default defineConfig({ plugins: [vue()], server: { proxy }, preview: { proxy } })
