<template>
  <div class="flex flex-col gap-5">
    <PageHeader eyebrow="Reports" title="報表">
      <nav aria-label="報表區塊" class="flex flex-wrap gap-1 rounded-[12px] border border-line bg-panel p-1">
        <button v-for="s in SECTIONS" :key="s.id" type="button" class="h-9 cursor-pointer rounded-lg border-0 bg-transparent px-3.5 text-sm text-muted hover:bg-panel2 hover:text-text"
          @click="jump(s.id)">{{ s.label }}</button>
      </nav>
    </PageHeader>

    <!-- 淨資產趨勢 -->
    <AppCard id="nw" class="flex scroll-mt-4 flex-col gap-3.5">
      <div class="flex flex-wrap items-center gap-3">
        <h2 class="m-0 mr-auto font-head text-[17px] font-semibold">淨資產趨勢</h2>
        <div class="num flex gap-4 text-xs text-muted">
          <span class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm bg-[var(--c2)]" />資產</span>
          <span class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm bg-exp" />負債</span>
          <span class="flex items-center gap-1.5"><span class="h-0.5 w-3.5 bg-accent" />淨資產</span>
        </div>
        <SegmentedControl v-model="range" :options="RANGES" label="期間" />
      </div>
      <template v-if="nw?.points.length">
        <div class="flex flex-wrap gap-x-7 gap-y-3">
          <div class="flex flex-col gap-0.5"><span class="lbl">{{ Number(last.month.slice(5)) }} 月淨資產</span><span class="num text-2xl font-semibold">{{ fmt(last.net_worth) }}</span></div>
          <div v-if="nw.points.length > 1" class="flex flex-col gap-0.5">
            <span class="lbl">{{ nw.points.length }} 個月變化</span>
            <span class="num text-2xl font-semibold" :class="change >= 0 ? 'text-inc' : 'text-exp'">{{ formatAmount(change, cur, { sign: 'always' }) }}<span v-if="changePct != null">（{{ changePct }}）</span></span>
          </div>
          <div class="flex flex-col gap-0.5"><span class="lbl">總負債</span><span class="num text-2xl font-semibold" :class="Number(last.liabilities) < 0 && 'text-exp'">{{ fmt(last.liabilities) }}</span></div>
        </div>
        <NetWorthChart :points="nw.points" :currency="cur" />
        <span class="text-xs text-muted">每月月底的餘額，外幣依當時匯率換算；本月以今天為準。投資市值會在階段 4 加入。</span>
      </template>
      <EmptyState v-else-if="nw" title="還沒有帳戶" text="建立帳戶後，這裡會顯示每月月底的資產與負債。" />
    </AppCard>

    <div class="flex flex-wrap items-center gap-3">
      <span class="lbl mr-auto">收支結構與每日收支 · 月份</span>
      <MonthSwitcher v-model="month" />
    </div>

    <section class="flex flex-wrap gap-4">
      <!-- 收支結構 -->
      <AppCard id="mix" class="flex min-w-0 flex-[1_1_520px] scroll-mt-4 flex-col gap-4">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="m-0 mr-auto font-head text-[17px] font-semibold">收支結構 · {{ month }}</h2>
          <SegmentedControl v-model="kind" :options="KINDS" label="收入或支出" />
        </div>
        <div v-if="mix?.items.length" class="flex flex-wrap items-center gap-6">
          <DonutChart :items="donut" :label="mixAria">
            <span class="lbl">{{ kind === 'expense' ? '總支出' : '總收入' }}</span>
            <span class="num text-[22px] font-semibold" :class="kind === 'expense' ? 'text-exp' : 'text-inc'">{{ fmt(mix.total) }}</span>
          </DonutChart>
          <div class="flex min-w-0 flex-[1_1_240px] flex-col">
            <div v-for="(c, i) in mix.items" :key="c.category_id ?? i" class="flex flex-col">
              <RouterLink :to="txLink(c.category_id)" class="flex min-h-11 items-center gap-2.5 border-b border-line text-text no-underline hover:text-accent">
                <span class="size-3 shrink-0 rounded-[3px]" :style="{ background: color(i) }" aria-hidden="true" />
                <span class="mr-auto truncate">{{ name(c.category_id) }}</span>
                <span class="num text-[13px] text-muted">{{ c.percentage }}%</span>
                <span class="num min-w-[70px] text-right text-sm">{{ fmt(c.amount) }}</span>
                <ChevronRight :size="14" class="text-muted" aria-hidden="true" />
              </RouterLink>
              <RouterLink v-for="s in c.children" :key="s.category_id" :to="txLink(s.category_id)"
                class="flex min-h-9 items-center gap-2.5 border-b border-dashed border-line pl-6 text-[13px] text-muted no-underline hover:text-accent">
                <span class="mr-auto truncate">└ {{ s.category_id === c.category_id ? `${name(s.category_id)}（未細分）` : name(s.category_id) }}</span>
                <span class="num">{{ fmt(s.amount) }}</span>
              </RouterLink>
            </div>
            <span class="pt-2.5 text-xs text-muted">點分類可查看對應的明細；轉帳不計入收支。</span>
          </div>
        </div>
        <p v-else-if="mix" class="m-0 py-10 text-center text-sm text-muted">{{ month }} 沒有{{ kind === 'expense' ? '支出' : '收入' }}。</p>
      </AppCard>

      <!-- 每日收支 -->
      <AppCard id="daily" class="flex min-w-0 flex-[1_1_520px] scroll-mt-4 flex-col gap-3.5">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="m-0 mr-auto font-head text-[17px] font-semibold">每日收支 · {{ month }}</h2>
          <div class="num flex gap-3.5 text-xs text-muted">
            <span class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm bg-inc" />收入</span>
            <span class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm bg-exp" />支出</span>
          </div>
        </div>
        <template v-if="daily">
          <div class="flex flex-wrap gap-x-6 gap-y-2">
            <div class="flex flex-col gap-0.5"><span class="lbl">收入</span><Amount :value="daily.income" :currency="cur" class="text-xl" /></div>
            <div class="flex flex-col gap-0.5"><span class="lbl">支出</span><Amount :value="-daily.expense" :currency="cur" class="text-xl" /></div>
            <div class="flex flex-col gap-0.5"><span class="lbl">日均支出</span><span class="num text-xl">{{ fmt(daily.average_expense) }}</span></div>
            <div class="flex flex-col gap-0.5"><span class="lbl">最高支出日</span>
              <span class="num text-xl">{{ daily.max_expense_day ? `${shortDate(daily.max_expense_day.date)} · ${fmt(daily.max_expense_day.expense)}` : '—' }}</span></div>
          </div>
          <DailyBars :days="daily.days" :currency="cur" :label="`${month} 每日收支：收入 ${fmt(daily.income)}、支出 ${fmt(daily.expense)}`" />
        </template>
      </AppCard>
    </section>

    <!-- 分類趨勢 -->
    <AppCard id="trend" class="flex scroll-mt-4 flex-col gap-3.5">
      <div class="flex flex-wrap items-center gap-3">
        <h2 class="m-0 mr-auto font-head text-[17px] font-semibold">分類趨勢</h2>
        <div class="w-full sm:w-[220px]"><SelectField v-model="trendCat" :options="trendOptions" placeholder="選擇分類" /></div>
        <div v-if="trend" class="num flex flex-wrap gap-3.5 text-xs text-muted">
          <span class="flex items-center gap-1.5"><span class="h-0.5 w-3.5 bg-accent" />每月{{ trend.kind === 'expense' ? '花費' : '收入' }}</span>
          <span v-if="currentBudget" class="flex items-center gap-1.5"><span class="w-3.5 border-t-2 border-dashed border-warn" />預算 {{ fmt(currentBudget) }}</span>
          <span class="flex items-center gap-1.5"><span class="w-3.5 border-t-2 border-dotted border-muted" />平均 {{ fmt(trend.average) }}</span>
        </div>
      </div>
      <template v-if="trend">
        <TrendLine :points="trend.points" :average="trend.average" :currency="cur" :label="trendAria" />
        <span class="text-xs text-muted">{{ trend.points.at(-1).month.slice(5) }} 月為截至今天的金額，平均不含本月。{{ trend.kind === 'expense' ? '紅色月份表示超出預算。' : '' }}</span>
      </template>
      <p v-else class="m-0 py-8 text-center text-sm text-muted">選擇分類後顯示最近 12 個月的趨勢。</p>
    </AppCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import Amount from '../components/ui/Amount.vue'
import AppCard from '../components/ui/AppCard.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import MonthSwitcher from '../components/ui/MonthSwitcher.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import SegmentedControl from '../components/ui/SegmentedControl.vue'
import SelectField from '../components/ui/SelectField.vue'
import DailyBars from '../components/charts/DailyBars.vue'
import DonutChart from '../components/charts/DonutChart.vue'
import NetWorthChart from '../components/charts/NetWorthChart.vue'
import TrendLine from '../components/charts/TrendLine.vue'
import { categoryOptions } from '../components/options'
import api from '../services/api'
import { useCategoriesStore } from '../stores/categories'
import { useUiStore } from '../stores/ui'
import { monthRange, shortDate, thisMonth } from '../utils/date'
import { formatAmount } from '../utils/money'

// 報表頁（SA FR-6、SD 5.1）：單頁四區塊，一律以本位幣計
const SECTIONS = [{ id: 'nw', label: '淨資產' }, { id: 'mix', label: '收支結構' }, { id: 'daily', label: '每日收支' }, { id: 'trend', label: '分類趨勢' }]
const RANGES = [{ value: '6', label: '6M' }, { value: '12', label: '12M' }, { value: '0', label: 'ALL' }]
const KINDS = [{ value: 'expense', label: '支出' }, { value: 'income', label: '收入' }]
const COLORS = ['var(--c1)', 'var(--c2)', 'var(--c3)', 'var(--c4)', 'var(--c5)', 'var(--c6)']
const categories = useCategoriesStore()
const ui = useUiStore()

const range = ref('12')
const month = ref(thisMonth())
const kind = ref('expense')
const trendCat = ref(null)
const nw = ref(null)
const mix = ref(null)
const daily = ref(null)
const trend = ref(null)

const cur = computed(() => nw.value?.base_currency ?? mix.value?.base_currency ?? 'TWD')
const fmt = (v) => formatAmount(v, cur.value, { sign: 'auto' })
const last = computed(() => nw.value.points.at(-1))
const change = computed(() => Number(last.value.net_worth) - Number(nw.value.points[0].net_worth))
const changePct = computed(() => {
  const first = Number(nw.value.points[0].net_worth)
  return first > 0 ? `${change.value >= 0 ? '+' : '−'}${Math.abs(change.value / first * 100).toFixed(1)}%` : null
})

const name = (id) => id ? categories.path(id) : '（未分類）'
const color = (i) => COLORS[Math.min(i, COLORS.length - 1)]
// 環形圖最多 6 段：前 5 大分類＋其他
const donut = computed(() => {
  const items = mix.value?.items ?? []
  const top = items.slice(0, 5).map((c, i) => ({ label: `${name(c.category_id)} ${c.percentage}%`, value: c.amount, color: color(i) }))
  const rest = items.slice(5).reduce((s, c) => s + Number(c.amount), 0)
  return rest ? [...top, { label: `其餘 ${items.length - 5} 項`, value: rest, color: COLORS[5] }] : top
})
const mixAria = computed(() => `${kind.value === 'expense' ? '支出' : '收入'}結構：` + (mix.value?.items ?? []).map(c => `${name(c.category_id)} ${Math.round(c.percentage)}%`).join('、'))
// 點分類跳到對應篩選條件的明細（SA FR-6.6）；明細頁的分類篩選會包含子類
const txLink = (categoryId) => ({ path: '/transactions', query: { month: month.value, kind: kind.value, ...(categoryId ? { category: categoryId } : {}) } })

const trendOptions = computed(() => [
  ...categoryOptions(categories.tree('expense')).map(g => ({ ...g, label: `支出 · ${g.label}` })),
  ...categoryOptions(categories.tree('income')).map(g => ({ ...g, label: `收入 · ${g.label}` }))
])
const currentBudget = computed(() => trend.value?.points.at(-1)?.budget)
const trendAria = computed(() => {
  if (!trend.value) return ''
  const ps = trend.value.points
  const max = ps.reduce((m, p) => Number(p.amount) > Number(m.amount) ? p : m, ps[0])
  return `${name(trendCat.value)}近 ${ps.length} 個月，最高 ${fmt(max.amount)}（${max.month}），平均 ${fmt(trend.value.average)}`
})

function jump (id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function loadNw () {
  nw.value = (await api.get('/reports/net-worth', { params: { months: range.value } })).data
}
async function loadMonth () {
  const { from, to } = monthRange(month.value)
  const [b, d] = await Promise.all([
    api.get('/reports/breakdown', { params: { from, to, kind: kind.value } }),
    api.get('/reports/daily', { params: { month: month.value } })
  ])
  mix.value = b.data
  daily.value = d.data
  // 分類趨勢預設選本月支出最多的分類
  if (!trendCat.value && kind.value === 'expense' && b.data.items[0]?.category_id) trendCat.value = b.data.items[0].category_id
}
async function loadTrend () {
  if (!trendCat.value) return
  trend.value = (await api.get('/reports/category-trend', { params: { category_id: trendCat.value, months: 12 } })).data
}

onMounted(async () => {
  await categories.ensure()
  await Promise.all([loadNw(), loadMonth()])
  if (!trendCat.value) trendCat.value = categories.tree('expense')[0]?.id ?? null
})
watch(range, loadNw)
watch([month, kind], loadMonth)
watch(trendCat, loadTrend)
watch(() => ui.ledgerVersion, () => Promise.all([loadNw(), loadMonth(), loadTrend()]))
</script>
