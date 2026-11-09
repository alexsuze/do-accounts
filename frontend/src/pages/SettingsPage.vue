<template>
  <div class="flex flex-col gap-6">
    <PageHeader eyebrow="Settings" title="設定" />

    <div class="flex flex-wrap items-start gap-4">
      <div class="flex min-w-0 flex-[1_1_460px] flex-col gap-4">
        <AppCard class="flex flex-wrap items-center gap-4">
          <img v-if="auth.user?.picture" :src="auth.user.picture" alt="" class="size-14 rounded-full" referrerpolicy="no-referrer">
          <span v-else class="flex size-14 items-center justify-center rounded-full bg-accent font-head text-[22px] font-bold text-accent-ink">{{ initial }}</span>
          <span class="mr-auto flex min-w-0 flex-col gap-1">
            <span class="text-[17px] font-semibold">{{ auth.user?.name }}</span>
            <span class="num truncate text-[13px] text-muted">{{ auth.user?.email }} · Google 帳號</span>
            <Badge v-if="auth.isAdmin" tone="accent" class="self-start">管理員</Badge>
          </span>
          <AppButton variant="danger" @click="logout"><LogOut :size="16" aria-hidden="true" />登出</AppButton>
        </AppCard>

        <AppCard>
          <h2 class="m-0 mb-1 font-head text-base font-semibold">外觀</h2>
          <div class="flex flex-wrap items-center gap-3 border-b border-line py-3.5">
            <span class="mr-auto">主題</span>
            <SegmentedControl :model-value="ui.mode" :options="modes" label="主題" @update:model-value="ui.setMode" />
          </div>
          <div class="flex flex-wrap items-center gap-3 py-3.5">
            <span class="mr-auto flex flex-col gap-1">
              <span>收支顏色</span>
              <span class="num text-[13px]"><span class="text-inc">收入 +1,200</span>　<span class="text-exp">支出 −350</span></span>
            </span>
            <SegmentedControl :model-value="ui.pnl" :options="pnls" label="收支顏色" @update:model-value="ui.setPnl" />
          </div>
        </AppCard>
      </div>

      <div class="flex min-w-0 flex-[1_1_360px] flex-col gap-4">
        <AppCard class="flex flex-col gap-2">
          <h2 class="m-0 font-head text-base font-semibold">記帳偏好</h2>
          <p class="m-0 text-sm text-muted">本位幣（目前 {{ auth.user?.base_currency || 'TWD' }}）、每週起始日、預算警示門檻會在階段 2 開放設定。</p>
        </AppCard>
        <AppCard v-if="auth.isAdmin" class="flex flex-wrap items-center gap-3">
          <span class="lbl mr-auto">管理員工具</span>
          <RouterLink to="/admin" class="text-sm">使用者核准 →</RouterLink>
        </AppCard>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { LogOut } from 'lucide-vue-next'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import Badge from '../components/ui/Badge.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import SegmentedControl from '../components/ui/SegmentedControl.vue'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const modes = [{ value: 'dark', label: '深色' }, { value: 'light', label: '淺色' }, { value: 'system', label: '跟隨系統' }]
const pnls = [{ value: 'green', label: '綠收紅支' }, { value: 'red', label: '紅收綠支' }]
const initial = computed(() => (auth.user?.name || auth.user?.email || '?').slice(0, 1).toUpperCase())

function logout () {
  auth.logout()
  router.replace({ name: 'login' })
}
</script>
