import { createApp } from 'vue'
import {
  ElAlert, ElButton, ElCheckbox, ElCheckboxGroup, ElEmpty, ElFormItem, ElIcon,
  ElInput, ElInputNumber, ElLoading, ElOption, ElPagination, ElSelect
} from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App).use(router).use(ElLoading)
const components = [ElAlert, ElButton, ElCheckbox, ElCheckboxGroup, ElEmpty, ElFormItem, ElIcon,
  ElInput, ElInputNumber, ElOption, ElPagination, ElSelect]
for (const component of components) app.component(component.name, component)
app.mount('#app')
