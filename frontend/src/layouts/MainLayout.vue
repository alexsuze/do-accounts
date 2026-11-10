<template>
  <div class="dot-grid flex min-h-screen">
    <!-- 表格模式需要全寬（示意圖），隱藏側邊欄，頁面左上有返回鍵 -->
    <AppSidebar v-if="!route.meta.wide" />
    <main class="min-w-0 flex-1 px-4 pt-5 pb-28 sm:px-6 lg:px-8 lg:pt-7 lg:pb-10">
      <RouterView />
    </main>
    <MobileTabBar />
    <BackToTop />
    <ShortcutHelp v-model:open="helpOpen" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from '../components/AppSidebar.vue'
import BackToTop from '../components/BackToTop.vue'
import MobileTabBar from '../components/MobileTabBar.vue'
import ShortcutHelp from '../components/ShortcutHelp.vue'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { shortcutFor } from '../utils/shortcuts'

// 進入主畫面時重新取得使用者資料（管理員身分、名稱可能已變）；失敗由 api.js 處理 401
const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()
const helpOpen = ref(false)

// 全域快捷鍵（SA FR-11.4）：有視窗開著時不觸發（Esc 由視窗自己處理）
function onKey (e) {
  const action = shortcutFor(e)
  if (!action || document.querySelector('[role="dialog"]')) return
  e.preventDefault()
  if (action === 'new') ui.openTx()
  else if (action === 'help') helpOpen.value = true
  else if (action === 'search') {
    if (route.name !== 'transactions') router.push({ name: 'transactions' })
    ui.focusSearch = true
  }
}
onMounted(() => {
  auth.refresh().catch(() => {})
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

// 主畫面捷徑「記帳」（manifest shortcuts）：#/?new=1 開啟記帳視窗
watch(() => route.query.new, (v) => {
  if (v !== '1') return
  ui.openTx()
  router.replace({ query: { ...route.query, new: undefined } })
}, { immediate: true })
</script>
