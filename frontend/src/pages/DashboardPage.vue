<template>
  <div class="flex flex-col gap-6">
    <PageHeader eyebrow="Dashboard" title="總覽">
      <span class="num rounded-[10px] border border-line bg-panel px-3.5 py-2.5 text-[13px] text-muted">{{ now }}</span>
    </PageHeader>

    <AppCard glow class="flex flex-col gap-2">
      <span class="lbl">Stage 0 · 基礎建設</span>
      <h2 class="m-0 font-head text-xl font-semibold">歡迎，{{ auth.user?.name || auth.user?.email }}</h2>
      <p class="m-0 text-sm leading-relaxed text-muted">登入、深淺色切換與部署已就緒。帳戶、分類與記帳會在階段 1 加入，屆時這裡會顯示淨資產與本月收支。</p>
    </AppCard>

    <section class="grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-4">
      <StatCard label="淨資產 · Net worth" value="—" hint="階段 1 開始計算" />
      <StatCard label="本月收入" value="—" tone="inc" hint="階段 1" />
      <StatCard label="本月支出" value="—" tone="exp" hint="階段 1" />
    </section>

    <AppCard class="flex flex-col gap-1">
      <h2 class="m-0 mb-2 font-head text-base font-semibold">連線狀態</h2>
      <div v-for="c in checks" :key="c.label" class="flex min-h-11 items-center gap-3 border-b border-line last:border-b-0">
        <span class="size-2 rounded-full" :class="c.ok === null ? 'bg-muted' : c.ok ? 'bg-inc' : 'bg-exp'" aria-hidden="true" />
        <span class="mr-auto">{{ c.label }}</span>
        <span class="num text-[13px]" :class="c.ok === false ? 'text-exp' : 'text-muted'">{{ c.text }}</span>
      </div>
    </AppCard>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import AppCard from '../components/ui/AppCard.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import StatCard from '../components/ui/StatCard.vue'
import api from '../services/api'
import { useAuthStore } from '../stores/auth'
import { useClock } from '../composables/useClock'

const auth = useAuthStore()
const now = useClock()

// 階段 0 的完成標準：GitHub Pages ↔ Railway ↔ Atlas 接通（SD 8）
const checks = reactive([
  { label: '後端 API（Railway）', ok: null, text: '檢查中…' },
  { label: '登入驗證（通行證）', ok: null, text: '檢查中…' }
])

onMounted(async () => {
  const t0 = performance.now()
  try {
    await api.get('/ping', { silent: true })
    Object.assign(checks[0], { ok: true, text: `正常 · ${Math.round(performance.now() - t0)} ms` })
  } catch {
    Object.assign(checks[0], { ok: false, text: '無法連線' })
  }
  try {
    const { data } = await api.get('/auth/me', { silent: true })
    Object.assign(checks[1], { ok: true, text: `正常 · 資料庫已讀到 ${data.email}` })
  } catch {
    Object.assign(checks[1], { ok: false, text: '驗證失敗' })
  }
})
</script>
