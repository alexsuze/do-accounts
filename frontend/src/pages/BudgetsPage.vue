<template>
  <div class="flex flex-col gap-5">
    <PageHeader eyebrow="Budgets" title="預算">
      <MonthSwitcher v-model="month" />
      <AppButton variant="ghost" :loading="copying" @click="copyPrev"><Copy :size="16" aria-hidden="true" />複製上月預算</AppButton>
      <AppButton @click="openNew()"><Plus :size="16" aria-hidden="true" />新增預算</AppButton>
    </PageHeader>

    <template v-if="v">
      <AppCard v-if="v.total" glow class="flex flex-wrap items-center gap-7 border-accent">
        <BudgetRing :percentage="v.total.percentage" :tone="tone(v.total)" />
        <div class="flex min-w-0 flex-[1_1_420px] flex-col gap-4">
          <div class="flex flex-wrap gap-x-7 gap-y-3">
            <div class="flex flex-col gap-1">
              <span class="lbl">{{ v.total.derived ? '分類預算合計' : '本月總預算' }}</span>
              <span class="flex items-center gap-2">
                <span class="num text-[26px] font-semibold">{{ fmt(v.total.amount) }}</span>
                <IconButton v-if="!v.total.derived" label="編輯總預算" @click="openEdit(v.total)"><Pencil :size="15" aria-hidden="true" /></IconButton>
              </span>
            </div>
            <div class="flex flex-col gap-1"><span class="lbl">已花費</span><Amount :value="-v.total.spent" :currency="cur" class="text-[26px] font-semibold" /></div>
            <div class="flex flex-col gap-1"><span class="lbl">{{ Number(v.total.remaining) < 0 ? '超支' : '剩餘' }}</span>
              <span class="num text-[26px] font-semibold" :class="Number(v.total.remaining) < 0 ? 'text-exp' : 'text-inc'">{{ fmt(Math.abs(v.total.remaining)) }}</span></div>
            <div v-if="daysLeft > 0 && Number(v.total.remaining) > 0" class="flex flex-col gap-1">
              <span class="lbl">剩 {{ daysLeft }} 天 · 每日可用</span>
              <span class="num text-[26px] font-semibold">{{ fmt(v.total.remaining / daysLeft) }}</span>
            </div>
          </div>
          <div class="flex flex-col gap-2">
            <BudgetBar thick accent :percentage="v.total.percentage" :projected="projPct(v.total)" :tone="tone(v.total)" :time-pct="timePct" />
            <div class="num flex flex-wrap gap-x-5 gap-y-1.5 text-xs text-muted">
              <span class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm" :class="BG[tone(v.total)]" />已花費 {{ pctText(v.total.percentage) }}</span>
              <span v-if="timePct != null" class="flex items-center gap-1.5"><span class="h-3 w-0.5 bg-text" />本月已過 {{ Math.round(timePct) }}%（{{ v.days_elapsed }} / {{ v.days_in_month }} 天）</span>
              <span v-if="timePct != null" class="flex items-center gap-1.5" :class="Number(v.total.projected) > Number(v.total.amount) ? 'text-exp' : 'text-warn'">
                <span class="size-2.5 rounded-sm border border-dashed border-current" />依目前速度，月底推估 {{ fmt(v.total.projected) }}（{{ pctText(projPct(v.total)) }}）
              </span>
            </div>
            <p v-if="v.total.derived" class="m-0 text-xs text-muted">沒有設定總預算，這裡用各分類預算加總。<button type="button" class="cursor-pointer border-0 bg-transparent p-0 text-accent underline" @click="openNew(TOTAL_PRESET)">設定總預算</button></p>
          </div>
        </div>
      </AppCard>

      <AppCard v-else>
        <EmptyState :icon="Target" title="這個月還沒有預算" text="可以設定當月總預算，或依分類設定；設成「每月循環」後，之後的月份會自動沿用。">
          <div class="flex flex-wrap justify-center gap-2.5">
            <AppButton variant="ghost" :loading="copying" @click="copyPrev">複製上月預算</AppButton>
            <AppButton @click="openNew()"><Plus :size="16" aria-hidden="true" />新增預算</AppButton>
          </div>
        </EmptyState>
      </AppCard>

      <section class="flex flex-wrap items-start gap-4">
        <AppCard :padded="false" class="min-w-0 flex-[3_1_560px] overflow-hidden">
          <div class="flex flex-wrap items-center gap-3 border-b border-line px-5 py-4">
            <h2 class="m-0 mr-auto font-head text-base font-semibold">分類預算</h2>
            <div class="num flex gap-3.5 text-xs text-muted">
              <span class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm bg-inc" />&lt; {{ v.warn_pct }}%</span>
              <span class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm bg-warn" />≥ {{ v.warn_pct }}%</span>
              <span class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm bg-exp" />超支</span>
            </div>
          </div>
          <p v-if="!v.items.length" class="m-0 px-5 py-8 text-center text-sm text-muted">還沒有分類預算。</p>
          <div v-for="i in v.items" :key="i.category_id" class="flex flex-col gap-2 border-b border-line px-5 py-3.5 last:border-b-0"
            :class="categories.byId(i.category_id)?.parent_id && 'pl-10'">
            <div class="flex flex-wrap items-center gap-2.5">
              <span class="size-2.5 shrink-0 rounded-full" :style="{ background: categories.root(i.category_id)?.color }" aria-hidden="true" />
              <span class="font-medium">{{ categories.path(i.category_id) }}</span>
              <Badge v-if="i.recurring">↻ 每月循環</Badge>
              <Badge v-if="i.inherited" tone="muted">沿用 {{ i.source_month }}</Badge>
              <Badge v-if="Number(i.remaining) < 0" tone="exp">超支 {{ fmt(-i.remaining) }}</Badge>
              <span class="mr-auto" />
              <span class="num hidden text-[13px] sm:inline">{{ fmt(i.spent) }} <span class="text-muted">/ {{ fmt(i.amount) }}</span></span>
              <span class="num w-14 text-right text-[13px] font-semibold" :class="TEXT[tone(i)]">{{ pctText(i.percentage) }}</span>
              <IconButton :label="`編輯 ${categories.path(i.category_id)} 預算`" @click="openEdit(i)"><Pencil :size="15" aria-hidden="true" /></IconButton>
            </div>
            <BudgetBar :percentage="i.percentage" :projected="projPct(i)" :tone="tone(i)" />
            <div class="num flex flex-wrap justify-between gap-x-3 text-[11px] text-muted">
              <span><span class="text-text sm:hidden">{{ fmt(i.spent) }} / {{ fmt(i.amount) }} · </span>{{ Number(i.remaining) >= 0 ? `剩 ${fmt(i.remaining)}` : `已超出 ${fmt(-i.remaining)}` }}</span>
              <span v-if="timePct != null">月底推估 {{ fmt(i.projected) }}</span>
            </div>
          </div>
        </AppCard>

        <div class="flex min-w-0 flex-[1_1_300px] flex-col gap-4">
          <AppCard class="flex flex-col gap-1">
            <h2 class="m-0 mb-2 font-head text-base font-semibold">未設預算的支出</h2>
            <p v-if="!v.unbudgeted.length" class="m-0 text-sm text-muted">這個月的支出都有對應的預算。</p>
            <div v-for="u in v.unbudgeted" :key="u.category_id" class="flex min-h-11 items-center gap-2.5 border-b border-line last:border-b-0">
              <span class="mr-auto">{{ categories.path(u.category_id) }}</span>
              <Amount :value="-u.spent" :currency="cur" class="text-[13px]" />
              <AppButton variant="ghost" @click="openNew(u.category_id)">設定</AppButton>
            </div>
          </AppCard>

          <AppCard class="flex flex-col gap-2.5">
            <h2 class="m-0 font-head text-base font-semibold">近 6 個月達成</h2>
            <div class="relative">
              <div class="grid h-24 grid-cols-6 items-end gap-1.5" role="img" :aria-label="historyAria">
                <div v-for="h in history" :key="h.month" class="flex h-full flex-col items-center justify-end gap-1" :title="h.tip">
                  <span class="num text-[10px] text-muted">{{ h.label }}</span>
                  <div class="w-[60%] rounded-t-sm" :class="h.cls" :style="{ height: `${h.height}px` }" />
                </div>
              </div>
              <div aria-hidden="true" class="absolute inset-x-0 border-t border-dashed border-muted opacity-70" :style="{ bottom: `${BAR_100}px` }" />
            </div>
            <div class="grid grid-cols-6 gap-1.5">
              <span v-for="h in history" :key="h.month" class="num text-center text-[11px] text-muted">{{ h.month.slice(5) }}月</span>
            </div>
            <span class="text-xs text-muted">{{ historyNote }}</span>
          </AppCard>
        </div>
      </section>
    </template>
    <AppCard v-else class="py-12 text-center text-muted">載入中…</AppCard>

    <BudgetDialog v-model:open="dialog.open" :month="month" :currency="cur" :item="dialog.item" :preset="dialog.preset" :taken="taken" @saved="load" />
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Copy, Pencil, Plus, Target } from 'lucide-vue-next'
import Amount from '../components/ui/Amount.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import Badge from '../components/ui/Badge.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import IconButton from '../components/ui/IconButton.vue'
import MonthSwitcher from '../components/ui/MonthSwitcher.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import BudgetBar from '../components/charts/BudgetBar.vue'
import BudgetRing from '../components/charts/BudgetRing.vue'
import BudgetDialog from '../components/BudgetDialog.vue'
import api from '../services/api'
import { useCategoriesStore } from '../stores/categories'
import { useUiStore } from '../stores/ui'
import { toast } from '../stores/toast'
import { budgetTone } from '../utils/charts'
import { shiftMonth, thisMonth } from '../utils/date'
import { formatAmount } from '../utils/money'

// 預算頁（SA FR-5、SD 5.1）。月份同步到網址 ?month=
const TOTAL_PRESET = '__total__'
const BG = { inc: 'bg-accent', warn: 'bg-warn', exp: 'bg-exp' }
const TEXT = { inc: 'text-inc', warn: 'text-warn', exp: 'text-exp' }
const BAR_100 = 62 // 近 6 個月長條圖中 100% 的高度（px）
const route = useRoute()
const router = useRouter()
const categories = useCategoriesStore()
const ui = useUiStore()
const month = ref(/^\d{4}-\d{2}$/.test(route.query.month) ? route.query.month : thisMonth())
const v = ref(null)
const copying = ref(false)
const dialog = reactive({ open: false, item: null, preset: null })

const cur = computed(() => v.value?.base_currency ?? 'TWD')
const fmt = (n) => formatAmount(n, cur.value, { sign: 'none' })
const pctText = (p) => `${Math.round(Number(p))}%`
const tone = (i) => budgetTone(i.percentage, v.value?.warn_pct)
const projPct = (i) => Number(i.amount) ? Number(i.projected) / Number(i.amount) * 100 : 0
const timePct = computed(() => v.value && month.value === thisMonth() ? v.value.days_elapsed / v.value.days_in_month * 100 : null)
const daysLeft = computed(() => v.value && month.value === thisMonth() ? v.value.days_in_month - v.value.days_elapsed : 0)
const taken = computed(() => [...(v.value?.total && !v.value.total.derived ? [null] : []), ...(v.value?.items ?? []).map(i => i.category_id)])

const history = computed(() => (v.value?.history ?? []).map(h => {
  const pct = h.budget ? Number(h.spent) / Number(h.budget) * 100 : null
  return {
    month: h.month,
    label: pct == null ? '—' : `${Math.round(pct)}%`,
    height: pct == null ? 2 : Math.max(2, Math.min(pct, 150) / 100 * BAR_100),
    cls: pct == null ? 'bg-line' : pct > 100 ? 'bg-exp' : pct >= (v.value.warn_pct) ? 'bg-warn' : 'bg-inc',
    tip: `${h.month}：花費 ${fmt(h.spent)}${h.budget ? ` / 預算 ${fmt(h.budget)}` : '（沒有預算）'}`
  }
}))
const historyAria = computed(() => history.value.map(h => `${h.month} ${h.label}`).join('、'))
const historyNote = computed(() => {
  const over = (v.value?.history ?? []).filter(h => h.budget && Number(h.spent) > Number(h.budget))
  return `虛線為 100%。${over.length ? `6 個月中 ${over.length} 個月超支（${over.map(h => h.month.slice(5) + ' 月').join('、')}）。` : '6 個月都沒有超支。'}`
})

async function load () {
  const m = month.value
  const [{ data }] = await Promise.all([api.get('/budgets', { params: { month: m } }), categories.ensure()])
  if (m === month.value) v.value = data
}

function openNew (preset = null) {
  Object.assign(dialog, { open: true, item: null, preset })
}
function openEdit (item) {
  Object.assign(dialog, { open: true, item, preset: null })
}

async function copyPrev () {
  copying.value = true
  try {
    const { data } = await api.post('/budgets/copy', { from_month: shiftMonth(month.value, -1), to_month: month.value })
    toast.success(data.copied ? `已複製 ${data.copied} 項預算` : '本月已有相同的預算，沒有需要複製的項目')
    await load()
  } finally {
    copying.value = false
  }
}

watch(month, (m) => {
  router.replace({ query: m === thisMonth() ? {} : { month: m } })
  load()
}, { immediate: true })
watch(() => ui.ledgerVersion, load)
</script>
