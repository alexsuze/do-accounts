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
