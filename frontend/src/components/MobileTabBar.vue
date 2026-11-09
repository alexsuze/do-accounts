<template>
  <nav aria-label="主選單" class="fixed inset-x-0 bottom-0 z-40 flex h-[76px] items-center justify-around border-t border-line bg-panel px-3 pb-3.5 pt-1.5 lg:hidden">
    <RouterLink v-for="t in left" :key="t.to" :to="t.to" :class="tabClass(t.to)"><component :is="t.icon" :size="20" aria-hidden="true" />{{ t.label }}</RouterLink>
    <button type="button" aria-label="記帳" class="-mt-6 flex size-14 items-center justify-center rounded-[18px] bg-accent text-accent-ink shadow-[0_0_0_6px_var(--bg),0_0_24px_var(--accent-soft)]"
      @click="ui.openTx()">
      <Plus :size="24" aria-hidden="true" />
    </button>
    <RouterLink :to="right.to" :class="tabClass(right.to)"><component :is="right.icon" :size="20" aria-hidden="true" />{{ right.label }}</RouterLink>
    <button type="button" :class="tabClass(null)" @click="more = true"><Ellipsis :size="20" aria-hidden="true" />更多</button>
  </nav>

  <DialogRoot v-model:open="more">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 z-50 bg-scrim lg:hidden" />
      <DialogContent class="fixed inset-x-0 bottom-0 z-50 flex max-h-[80vh] flex-col gap-1 overflow-y-auto rounded-t-[18px] border-t border-line bg-panel p-4 pb-8 text-text lg:hidden">
        <DialogTitle class="lbl px-2 pb-2">全部功能</DialogTitle>
        <DialogDescription class="sr-only">選擇要前往的頁面</DialogDescription>
        <RouterLink v-for="n in all" :key="n.to" :to="n.to" class="flex min-h-11 items-center gap-3 rounded-[10px] px-3 text-text no-underline hover:bg-panel2" @click="more = false">
          <span class="num w-[18px] text-[11px] text-muted">{{ n.no }}</span>{{ n.label }}
        </RouterLink>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import { ChartColumn, Ellipsis, House, List, Plus } from 'lucide-vue-next'
import { NAV } from '../router'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

// 手機與平板（< 1024px）的底部導覽＋「更多」抽屜（SD 5.6）
const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()
const more = ref(false)
const left = [{ to: '/', label: '總覽', icon: House }, { to: '/transactions', label: '明細', icon: List }]
const right = { to: '/reports', label: '報表', icon: ChartColumn }
const all = computed(() => auth.isAdmin ? [...NAV, { no: '11', label: '管理', to: '/admin' }] : NAV)
const tabClass = (to) => ['flex min-h-12 min-w-14 flex-col items-center justify-center gap-0.5 text-[11px] no-underline',
  to && route.path === to ? 'text-accent' : 'text-muted']
</script>
