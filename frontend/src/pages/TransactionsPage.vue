<template>
  <div class="flex flex-col gap-5">
    <PageHeader eyebrow="Transactions" title="明細">
      <AppButton variant="ghost" :loading="exporting" @click="exportCsv"><Download :size="16" aria-hidden="true" />匯出 CSV</AppButton>
      <AppButton variant="ghost" :to="{ path: '/transactions/grid', query: gridQuery }"><Table :size="16" aria-hidden="true" />表格編輯</AppButton>
      <AppButton title="記帳（快捷鍵 N）" @click="ui.openTx()"><Plus :size="16" aria-hidden="true" />記帳</AppButton>
    </PageHeader>

    <AppCard class="flex flex-wrap items-end gap-3">
      <MonthSwitcher :model-value="filter.month" @update:model-value="m => setFilter({ month: m })" />
      <SegmentedControl :model-value="filter.kind || 'all'" :options="kindOptions" label="交易類型" @update:model-value="k => setFilter({ kind: k === 'all' ? null : k })" />
      <div class="w-full sm:w-48"><SelectField :model-value="filter.account" :options="accountOpts" none-label="全部帳戶" placeholder="全部帳戶" @update:model-value="v => setFilter({ account: v })" /></div>
      <div class="w-full sm:w-48"><SelectField :model-value="filter.category" :options="categoryOpts" none-label="全部分類" placeholder="全部分類" @update:model-value="v => setFilter({ category: v })" /></div>
      <label class="flex h-11 min-w-0 flex-[1_1_200px] items-center gap-2 rounded-[10px] border border-line bg-panel2 px-3 text-muted focus-within:border-accent">
        <Search :size="16" aria-hidden="true" />
        <input ref="searchEl" v-model="keyword" aria-label="搜尋項目或備註（快捷鍵 /）" placeholder="搜尋項目、備註…" class="w-full border-0 bg-transparent text-sm text-text outline-none placeholder:text-muted" @keydown.enter="setFilter({ q: keyword })" @blur="setFilter({ q: keyword })">
      </label>
    </AppCard>

    <section class="grid grid-cols-[repeat(auto-fit,minmax(170px,1fr))] gap-3">
      <StatCard label="收入" :value="fmtSigned(sum.income)" tone="inc" />
      <StatCard label="支出" :value="fmtSigned(-sum.expense)" tone="exp" />
      <StatCard label="結餘" :value="fmtSigned(sum.income - sum.expense)" />
      <StatCard label="筆數" :value="String(page?.total ?? 0)" :hint="otherCurrencies" />
    </section>

    <div v-if="selected.size" class="flex flex-wrap items-center gap-2.5 rounded-xl border border-accent bg-accent-soft px-4 py-2.5">
      <span class="num mr-auto text-[13px] text-accent">已選取 {{ selected.size }} 筆</span>
      <AppButton size="sm" variant="ghost" @click="bulkOpen = true">改分類／加標籤</AppButton>
      <AppButton size="sm" variant="ghost" @click="selected.clear()">取消選取</AppButton>
      <AppButton size="sm" variant="danger" @click="confirmBulkDelete = true">刪除</AppButton>
    </div>

    <AppCard :padded="false" class="overflow-hidden">
      <template v-for="day in days" :key="day.date">
        <div class="flex items-center gap-3 border-b border-line bg-panel2 px-5 py-2.5">
          <label class="flex min-h-8 items-center"><input type="checkbox" :checked="day.items.every(t => selected.has(t.id))" :aria-label="`選取 ${day.date} 全部`" class="size-4 accent-[var(--accent)]" @change="toggleDay(day)"></label>
          <span class="num text-[13px] font-semibold">{{ day.date }}</span>
          <span class="mr-auto text-xs text-muted">星期{{ weekday(day.date) }}</span>
          <span class="num text-xs text-muted">當日小計</span>
          <Amount :value="day.net" :currency="base" class="text-[13px]" />
        </div>
        <TransactionRow v-for="t in day.items" :key="t.id" :tx="t" selectable :selected="selected.has(t.id)" @toggle="toggle(t.id)" @open="ui.openTx({ transaction: t })" />
      </template>
      <EmptyState v-if="page && !page.items.length" title="這段期間沒有交易" :text="hasFilter ? '試試看調整篩選條件。' : '按「記帳」新增第一筆。'" />
      <div v-if="!page" class="py-12 text-center text-muted">載入中…</div>
      <div v-if="page && page.total > SIZE" class="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5">
        <span class="num text-xs text-muted">第 {{ page.page * SIZE + 1 }}–{{ Math.min((page.page + 1) * SIZE, page.total) }} 筆，共 {{ page.total }} 筆</span>
        <div class="flex gap-1.5">
          <AppButton size="sm" variant="ghost" :disabled="page.page === 0" @click="setFilter({ page: page.page - 1 }, false)">上一頁</AppButton>
          <AppButton size="sm" variant="ghost" :disabled="(page.page + 1) * SIZE >= page.total" @click="setFilter({ page: page.page + 1 }, false)">下一頁</AppButton>
        </div>
      </div>
    </AppCard>

    <AppDialog v-model:open="bulkOpen" title="批次修改" :description="`套用到選取的 ${selected.size} 筆交易`" :width="460">
      <div class="flex flex-col gap-4">
        <SelectField v-model="bulk.category_id" label="改成分類" :options="bulkCategoryOpts" none-label="（不變更）" placeholder="（不變更）" hint="只能套用到同為收入或同為支出的交易" />
        <TextField v-model="bulk.add" label="加上標籤" placeholder="#出差" />
        <TextField v-model="bulk.remove" label="移除標籤" placeholder="#聚餐" />
      </div>
      <template #footer>
        <AppButton variant="ghost" class="ml-auto" @click="bulkOpen = false">取消</AppButton>
        <AppButton :loading="busy" @click="applyBulk">套用</AppButton>
      </template>
    </AppDialog>
    <ConfirmDialog v-model:open="confirmBulkDelete" danger :title="`刪除選取的 ${selected.size} 筆交易？`" description="刪除後無法復原，帳戶餘額會跟著更新。" confirm-text="刪除" @confirm="bulkDelete" />
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download, Plus, Search, Table } from 'lucide-vue-next'
import Amount from '../components/ui/Amount.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import AppDialog from '../components/ui/AppDialog.vue'
import ConfirmDialog from '../components/ui/ConfirmDialog.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import MonthSwitcher from '../components/ui/MonthSwitcher.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import SegmentedControl from '../components/ui/SegmentedControl.vue'
import SelectField from '../components/ui/SelectField.vue'
import StatCard from '../components/ui/StatCard.vue'
import TextField from '../components/ui/TextField.vue'
import TransactionRow from '../components/TransactionRow.vue'
import { accountOptions, categoryOptions } from '../components/options'
import api from '../services/api'
import { useAccountsStore } from '../stores/accounts'
import { useAuthStore } from '../stores/auth'
import { useCategoriesStore } from '../stores/categories'
import { useUiStore } from '../stores/ui'
import { toast } from '../stores/toast'
import { monthRange, thisMonth, weekday } from '../utils/date'
import { download } from '../services/download'
import { formatAmount } from '../utils/money'

// 明細（SA FR-3.4、3.5、3.9）。篩選條件同步到網址，可重新整理、可返回
const SIZE = 50
const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const auth = useAuthStore()
const accounts = useAccountsStore()
const categories = useCategoriesStore()
const base = computed(() => auth.user?.base_currency || 'TWD')

const filter = computed(() => ({
  month: /^\d{4}-\d{2}$/.test(route.query.month) ? route.query.month : thisMonth(),
  kind: route.query.kind || null,
  account: route.query.account || null,
  category: route.query.category || null,
  q: route.query.q || '',
  page: Number(route.query.page) || 0
}))
// 表格編輯：帶目前的篩選條件；month 固定帶上，避免沒有任何條件時被當成空白表格
const gridQuery = computed(() => ({ ...route.query, month: filter.value.month, page: undefined }))
const hasFilter = computed(() => !!(filter.value.kind || filter.value.account || filter.value.category || filter.value.q))
const keyword = ref(filter.value.q)
const page = ref(null)
const selected = reactive(new Set())
const bulkOpen = ref(false)
const confirmBulkDelete = ref(false)
const busy = ref(false)
const bulk = reactive({ category_id: null, add: '', remove: '' })

const kindOptions = [{ value: 'all', label: '全部' }, { value: 'expense', label: '支出' }, { value: 'income', label: '收入' }, { value: 'transfer', label: '轉帳' }]
const accountOpts = computed(() => accountOptions(accounts.list, { keep: accounts.list.map(a => a.id), showBalance: false }))
const categoryOpts = computed(() => [
  ...categoryOptions(categories.tree('expense')).map(g => ({ ...g, label: '支出 · ' + g.label })),
  ...categoryOptions(categories.tree('income')).map(g => ({ ...g, label: '收入 · ' + g.label }))
])
const bulkCategoryOpts = categoryOpts

const sum = computed(() => {
  const s = page.value?.summary?.[base.value]
  return { income: Number(s?.income ?? 0), expense: Number(s?.expense ?? 0) }
})
const otherCurrencies = computed(() => Object.keys(page.value?.summary ?? {}).filter(c => c !== base.value).map(c => `含 ${c} 交易`).join('、'))
const fmtSigned = (v) => formatAmount(v, base.value, { sign: 'always' })

// 依日期分組，小計用後端算好的整段期間每日合計
const days = computed(() => {
  if (!page.value) return []
  const totals = Object.fromEntries((page.value.daily_totals ?? []).map(d => [d.date, d.by_currency?.[base.value]]))
  const groups = []
  for (const t of page.value.items) {
    let g = groups.at(-1)
    if (!g || g.date !== t.date) {
      const tt = totals[t.date]
      g = { date: t.date, items: [], net: tt ? Number(tt.income) - Number(tt.expense) : 0 }
      groups.push(g)
    }
    g.items.push(t)
  }
  return groups
})

function setFilter (patch, resetPage = true) {
  const next = { ...filter.value, ...patch }
  if (resetPage && patch.page === undefined) next.page = 0
  const query = {}
  if (next.month !== thisMonth()) query.month = next.month
  for (const k of ['kind', 'account', 'category', 'q']) if (next[k]) query[k] = next[k]
  if (next.page) query.page = String(next.page)
  router.replace({ query })
}

// 篩選條件 → API 參數（列表與 CSV 匯出共用）
function filterParams (f) {
  const { from, to } = monthRange(f.month)
  const params = { from, to }
  if (f.kind) params.kind = f.kind
  if (f.account) params.account_id = f.account
  if (f.category) params.category_id = f.category
  if (f.q) params.q = f.q
  return params
}

// 依目前的篩選條件匯出（SA FR-10.1）
const exporting = ref(false)
async function exportCsv () {
  exporting.value = true
  try {
    await download('/export/transactions', `do-accounts-${filter.value.month}${hasFilter.value ? '-篩選' : ''}.csv`, filterParams(filter.value))
  } finally {
    exporting.value = false
  }
}

async function load () {
  const f = filter.value
  const params = { ...filterParams(f), page: f.page, size: SIZE }
  const [res] = await Promise.all([api.get('/transactions', { params }), accounts.ensure(), categories.ensure()])
  page.value = res.data
  selected.clear()
}

watch(filter, load, { immediate: true, deep: true })
// 快捷鍵 /：聚焦搜尋框（從別頁按時，頁面掛載後也要聚焦）
const searchEl = ref(null)
watch(() => ui.focusSearch, (v) => {
  if (!v) return
  nextTick(() => { searchEl.value?.focus(); ui.focusSearch = false })
}, { immediate: true })
watch(() => ui.ledgerVersion, load)

function toggle (id) { selected.has(id) ? selected.delete(id) : selected.add(id) }
function toggleDay (day) {
  const all = day.items.every(t => selected.has(t.id))
  day.items.forEach(t => all ? selected.delete(t.id) : selected.add(t.id))
}

const tagsOf = (s) => s.split(/[\s,，]+/).map(x => x.replace(/^#/, '')).filter(Boolean)
async function applyBulk () {
  busy.value = true
  try {
    const { data } = await api.post('/transactions/bulk-update', { ids: [...selected], category_id: bulk.category_id, add_tags: tagsOf(bulk.add), remove_tags: tagsOf(bulk.remove) })
    toast.success(`已修改 ${data.updated} 筆交易`)
    bulkOpen.value = false
    Object.assign(bulk, { category_id: null, add: '', remove: '' })
    ui.ledgerChanged()
  } finally { busy.value = false }
}
async function bulkDelete () {
  const { data } = await api.post('/transactions/bulk-delete', { ids: [...selected] })
  toast.success(`已刪除 ${data.deleted} 筆交易`)
  ui.ledgerChanged()
  accounts.load().catch(() => {})
}
</script>
