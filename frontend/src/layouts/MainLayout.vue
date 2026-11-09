<template>
  <div class="dot-grid flex min-h-screen">
    <AppSidebar />
    <main class="min-w-0 flex-1 px-4 pt-5 pb-28 sm:px-6 lg:px-8 lg:pt-7 lg:pb-10">
      <RouterView />
    </main>
    <MobileTabBar />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import AppSidebar from '../components/AppSidebar.vue'
import MobileTabBar from '../components/MobileTabBar.vue'
import { useAuthStore } from '../stores/auth'

// 進入主畫面時重新取得使用者資料（管理員身分、名稱可能已變）；失敗由 api.js 處理 401
const auth = useAuthStore()
onMounted(() => { auth.refresh().catch(() => {}) })
</script>
