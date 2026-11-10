<template>
  <aside class="sticky top-0 hidden h-screen w-[248px] shrink-0 flex-col gap-7 overflow-y-auto border-r border-line bg-panel px-4 py-6 lg:flex">
    <RouterLink to="/" class="px-2 text-text no-underline"><AppLogo /></RouterLink>
    <nav aria-label="主選單" class="flex flex-col gap-0.5">
      <RouterLink v-for="n in items" :key="n.to" :to="n.to" :class="linkClass(n)" :aria-current="isActive(n) ? 'page' : undefined">
        <span class="num w-[18px] text-[11px] opacity-60">{{ n.no }}</span>
        <span>{{ n.label }}</span>
      </RouterLink>
    </nav>
    <div class="mt-auto flex flex-col gap-3">
      <RouterLink v-if="fx.latest.length" to="/settings" class="flex flex-col gap-1.5 rounded-[14px] border border-line bg-panel2 p-3.5 text-text no-underline hover:border-accent"
        aria-label="匯率（前往設定）">
        <span class="lbl">匯率 · {{ shortDate(fx.date) }}</span>
        <span v-for="r in fx.latest" :key="r.currency" class="num flex justify-between text-[13px]"><span>{{ r.currency }}</span><span>{{ Number(r.rate).toFixed(r.rate >= 1 ? 3 : 4) }}</span></span>
      </RouterLink>
      <div class="rounded-[14px] border border-line bg-panel2 p-3.5">
        <div class="lbl">Now · 台北</div>
        <div class="num mt-1 text-[13px]">{{ now }}</div>
      </div>
      <div class="flex items-center gap-2.5 px-1">
        <img v-if="auth.user?.picture" :src="auth.user.picture" alt="" class="size-8 rounded-full" referrerpolicy="no-referrer">
        <span v-else class="flex size-8 items-center justify-center rounded-full bg-accent font-head font-bold text-accent-ink">{{ initial }}</span>
        <span class="min-w-0 flex-1 truncate text-sm">{{ auth.user?.name || auth.user?.email }}</span>
      </div>
      <span class="px-1 text-xs text-muted">按 <kbd class="num rounded border border-line px-1">?</kbd> 查看快捷鍵</span>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppLogo from './ui/AppLogo.vue'
import { NAV } from '../router'
import { useAuthStore } from '../stores/auth'
import { useClock } from '../composables/useClock'
import { useFxStore } from '../stores/fx'
import { shortDate } from '../utils/date'

// 桌面側邊欄（≥ 1024px，SD 5.6）
const auth = useAuthStore()
const route = useRoute()
const now = useClock()
const fx = useFxStore()
onMounted(() => fx.ensure())
const items = computed(() => auth.isAdmin ? [...NAV, { no: '11', label: '管理', to: '/admin' }] : NAV)
const initial = computed(() => (auth.user?.name || auth.user?.email || '?').slice(0, 1).toUpperCase())

// 子路徑也算選中（/accounts/123 → 帳戶），但本身就在選單上的子路徑（/transactions/grid）除外
const isActive = (n) => n.to === '/'
  ? route.path === '/'
  : route.path === n.to || (route.path.startsWith(n.to + '/') && !items.value.some(o => o.to === route.path))
const linkClass = (n) => ['flex min-h-11 items-center gap-2.5 rounded-[10px] px-3 no-underline transition-colors',
  isActive(n) ? 'bg-accent-soft text-accent' : 'text-muted hover:bg-panel2 hover:text-text']
</script>
