<template>
  <div class="flex flex-col gap-6">
    <PageHeader eyebrow="Dashboard" title="總覽">
      <span class="num hidden rounded-[10px] border border-line bg-panel px-3.5 py-2.5 text-[13px] text-muted md:inline">{{ now }}</span>
      <AppButton title="記帳（快捷鍵 N）" @click="ui.openTx()"><Plus :size="16" aria-hidden="true" />記帳</AppButton>
    </PageHeader>

    <template v-if="d">
      <section class="grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-4">
        <StatCard glow label="淨資產 · Net worth" :value="fmt(d.net_worth, { symbol: true })" :hint="rateHint" />
        <StatCard label="總資產 · Assets" :value="fmt(d.assets)" :hint="Number(d.investments) ? `含投資市值 ${fmt(d.investments)}` : `${d.base_currency}，含外幣、不含總覽隱藏的帳戶`" />
        <StatCard label="總負債 · Liabilities" :value="fmt(d.liabilities)" :tone="Number(d.liabilities) < 0 ? 'exp' : ''" hint="信用卡、貸款與負餘額帳戶" />
      </section>

      <section class="flex flex-wrap gap-4">
        <AppCard class="flex min-w-0 flex-[2_1_480px] flex-col gap-3">
          <div class="flex flex-wrap items-center gap-3">
            <h2 class="m-0 mr-auto font-head text-base font-semibold">淨資產趨勢</h2>
            <SegmentedControl v-model="range" :options="RANGES" label="期間" />
          </div>
          <template v-if="trend.length > 1">
            <BalanceChart :points="trend.map(p => ({ balance: p.net_worth }))" :label="trendAria" />
            <div class="num flex justify-between text-[11px] text-muted">
              <span v-for="p in trendLabels" :key="p">{{ p }}</span>
            </div>
          </template>
          <p v-else class="m-0 py-10 text-center text-sm text-muted">累積兩個月以上的資料後顯示趨勢。</p>
          <RouterLink to="/reports" class="self-end text-[13px]">完整報表 →</RouterLink>
        </AppCard>

        <AppCard class="flex min-w-0 flex-[1_1_300px] flex-col gap-4">
          <div class="flex items-center"><h2 class="m-0 mr-auto font-head text-base font-semibold">本月收支 · {{ Number(d.month.slice(5)) }} 月</h2>
            <RouterLink :to="{ path: '/transactions', query: { month: d.month } }" class="text-[13px]">明細 →</RouterLink></div>
          <div class="flex gap-3">
            <div class="flex flex-1 flex-col gap-1 rounded-[10px] bg-panel2 p-3"><span class="lbl">收入</span><Amount :value="d.month_totals.income" :currency="d.base_currency" class="text-xl" /></div>
            <div class="flex flex-1 flex-col gap-1 rounded-[10px] bg-panel2 p-3"><span class="lbl">支出</span><Amount :value="-d.month_totals.expense" :currency="d.base_currency" class="text-xl" /></div>
          </div>
          <div class="flex justify-between text-sm"><span class="text-muted">結餘</span><Amount :value="d.month_totals.income - d.month_totals.expense" :currency="d.base_currency" /></div>
          <div v-if="budgetRows.length" class="flex flex-col gap-2">
            <template v-for="b in budgetRows" :key="b.key">
              <div class="flex justify-between gap-2 text-[13px]"><span class="truncate">{{ b.label }}</span>
                <span class="num" :class="TEXT[b.tone]">{{ fmt(b.spent) }} / {{ fmt(b.amount) }} · {{ Math.round(b.percentage) }}%</span></div>
              <BudgetBar :percentage="b.percentage" :tone="b.tone" :accent="b.key === 'total'" />
            </template>
            <span v-if="d.budget.total" class="num text-xs text-muted">依目前速度，月底約 {{ fmt(-d.budget.total.projected) }}</span>
            <RouterLink to="/budgets" class="self-end text-[13px]">預算 →</RouterLink>
          </div>
          <p v-else class="m-0 text-xs text-muted">還沒有設定本月預算。<RouterLink to="/budgets">設定預算 →</RouterLink></p>
        </AppCard>
      </section>

      <AppCard :padded="false" class="flex min-w-0 flex-col">
        <div class="flex items-center px-5 pt-5 pb-2"><h2 class="m-0 mr-auto font-head text-base font-semibold">最近交易</h2><RouterLink to="/transactions" class="text-[13px]">全部明細 →</RouterLink></div>
        <TransactionRow v-for="t in d.recent" :key="t.id" :tx="t" show-date @open="ui.openTx({ transaction: t })" />
        <EmptyState v-if="!d.recent.length" title="還沒有任何交易" text="先在「帳戶」頁建立帳戶，再按「記帳」開始。">
          <AppButton v-if="!accounts.active.length" variant="ghost" to="/accounts">建立帳戶</AppButton>
          <AppButton v-else @click="ui.openTx()"><Plus :size="16" aria-hidden="true" />記第一筆</AppButton>
        </EmptyState>
      </AppCard>

      <AppCard class="flex flex-col gap-3">
        <div class="flex items-center"><h2 class="m-0 mr-auto font-head text-base font-semibold">帳戶</h2><RouterLink to="/accounts" class="text-[13px]">管理帳戶 →</RouterLink></div>
        <EmptyState v-if="!accounts.groups.length" title="還沒有帳戶" text="建立錢包、銀行、信用卡等帳戶後，這裡會依類型顯示餘額。">
          <AppButton to="/accounts"><Plus :size="16" aria-hidden="true" />建立帳戶</AppButton>
        </EmptyState>
        <div class="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-x-6 gap-y-4">
          <div v-for="g in visibleGroups" :key="g.value" class="flex flex-col gap-1">
            <div class="flex justify-between py-1"><span class="lbl">{{ g.label }} · {{ g.en }}</span></div>
            <RouterLink v-for="a in g.items" :key="a.id" :to="`/accounts/${a.id}`"
              class="flex min-h-11 items-center gap-2.5 rounded-[10px] bg-panel2 px-3 text-text no-underline hover:brightness-110">
              <span class="size-1.5 rounded-full bg-accent" aria-hidden="true" />
              <span class="mr-auto truncate">{{ a.name }}</span>
              <span v-if="a.currency !== d.base_currency" class="num text-xs text-muted">{{ approx(a) }}</span>
              <Amount :value="a.balance" :currency="a.currency" sign="auto" :plain="Number(a.balance) >= 0" class="text-sm font-medium" />
            </RouterLink>
          </div>
        </div>
      </AppCard>
    </template>
    <AppCard v-else-if="!error" class="py-12 text-center text-muted">載入中…</AppCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
import Amount from '../components/ui/Amount.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import StatCard from '../components/ui/StatCard.vue'
import SegmentedControl from '../components/ui/SegmentedControl.vue'
import TransactionRow from '../components/TransactionRow.vue'
import BalanceChart from '../components/BalanceChart.vue'
import BudgetBar from '../components/charts/BudgetBar.vue'
import api from '../services/api'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { useFxStore } from '../stores/fx'
import { useUiStore } from '../stores/ui'
import { useClock } from '../composables/useClock'
import { formatAmount } from '../utils/money'
import { budgetTone } from '../utils/charts'
import { shortDate } from '../utils/date'

// 總覽（SA FR-7）：外幣帳戶依最新匯率換算後併入淨資產
const RANGES = [{ value: '6', label: '6M' }, { value: '12', label: '12M' }, { value: '0', label: 'ALL' }]
const TEXT = { inc: '', warn: 'text-warn', exp: 'text-exp' }
const ui = useUiStore()
const accounts = useAccountsStore()
const categories = useCategoriesStore()
const fx = useFxStore()
const now = useClock()
const d = ref(null)
const error = ref(false)
const range = ref('6')
const trend = ref([])

const fmt = (v, opt = {}, cur) => formatAmount(v, cur ?? d.value?.base_currency ?? 'TWD', { sign: 'auto', ...opt })
const visibleGroups = computed(() => accounts.groups.map(g => ({ ...g, items: g.items.filter(a => a.visible) })).filter(g => g.items.length))
const rateHint = computed(() => {
  const parts = [d.value?.rates_date ? `外幣依 ${shortDate(d.value.rates_date)} 匯率換算` : `以 ${d.value?.base_currency} 計`]
  if (d.value?.hidden_accounts) parts.push(`未含隱藏帳戶 ${d.value.hidden_accounts} 個`)
  return parts.join(' · ')
})
// 外幣帳戶加註約當本位幣（FR-7.2）
const approx = (a) => {
  const v = fx.toBase(a.balance, a.currency, d.value.base_currency)
  return v == null ? a.currency : `≈ ${formatAmount(v, d.value.base_currency)}`
}
const budgetRows = computed(() => {
  const b = d.value?.budget
  if (!b) return []
  const rows = b.total ? [{ key: 'total', label: b.total.derived ? '分類預算合計' : '總預算', ...b.total }] : []
  for (const i of b.top) rows.push({ key: i.category_id, label: categories.path(i.category_id), ...i })
  return rows.map(r => ({ ...r, tone: budgetTone(r.percentage, b.warn_pct) }))
})
const trendLabels = computed(() => {
  const ps = trend.value
  const idx = ps.length <= 7 ? ps.map((_, i) => i) : [0, Math.round((ps.length - 1) / 3), Math.round((ps.length - 1) * 2 / 3), ps.length - 1]
  return idx.map(i => `${ps[i].month.slice(2).replace('-', '/')}`)
})
const trendAria = computed(() => trend.value.length ? `淨資產由 ${fmt(trend.value[0].net_worth)} 變為 ${fmt(trend.value.at(-1).net_worth)}` : '')

async function load () {
  try {
    const [res] = await Promise.all([api.get('/reports/dashboard'), accounts.load(), categories.ensure(), fx.load().catch(() => {}), loadTrend()])
    d.value = res.data
    error.value = false
  } catch {
    error.value = true
  }
}
async function loadTrend () {
  trend.value = (await api.get('/reports/net-worth', { params: { months: range.value }, silent: true }).catch(() => ({ data: { points: [] } }))).data.points
}
onMounted(load)
watch(() => ui.ledgerVersion, load)
watch(range, loadTrend)
</script>
