import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './css/main.css'

import App from './App.vue'
import router from './router'
import { useUiStore } from './stores/ui'

const app = createApp(App)
app.use(createPinia())
useUiStore().init()
app.use(router)
app.mount('#app')

// PWA：正式環境註冊 Service Worker（開發時不註冊，避免快取干擾熱更新）
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`, { scope: import.meta.env.BASE_URL }).catch(() => {})
  })
}
