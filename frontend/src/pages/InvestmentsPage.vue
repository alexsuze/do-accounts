<template>
  <div class="flex flex-col gap-5">
    <PageHeader eyebrow="Investments" title="投資">
      <SegmentedControl v-if="investAccounts.length > 1" :model-value="account" :options="accountPills" label="投資帳戶" @update:model-value="setAccount" />
      <AppButton variant="ghost" :loading="busy === 'quotes'" @click="refreshQuotes"><RefreshCw :size="16" aria-hidden="true" />更新報價</AppButton>
      <AppButton @click="ui.openTrade({ preset: { account_id: account === 'all' ? null : account } })"><Plus :size="16" aria-hidden="true" />新增投資交易</AppButton>
    </PageHeader>

    <AppCard v-if="loaded && !investAccounts.length">
      <EmptyState :icon="TrendingUp" title="還沒有投資帳戶" text="先在帳戶頁建立類型為「投資」的帳戶（例如券商交割戶、複委託），再回來新增投資交易。">
        <AppButton to="/accounts"><Plus :size="16" aria-hidden="true" />建立帳戶</AppButton>
      </EmptyState>
    </AppCard>

    <template v-else-if="s">
      <section class="grid grid-cols-2 gap-3 md:grid-cols-[repeat(auto-fit,minmax(190px,1fr))] md:gap-4">
        <StatCard glow class="col-span-2 md:col-span-1" label="總市值" :value="fmt(s.market_value)" :hint="`成本 ${fmt(s.cost)}`" />
        <StatCard label="未實現損益" :value="fmt(s.unrealized, 'always')" :tone="tone(s.unrealized)" :hint="pct(s.unrealized_pct)" />
        <StatCard :label="`已實現 · ${year}`" :value="fmt(s.realized_this_year, 'always')" :tone="tone(s.realized_this_year)" :hint="`賣出 ${s.sells_this_year} 筆`" />
        <StatCard label="累積股利" :value="fmt(s.dividends_total)" :hint="`今年 ${s.dividend_count_this_year} 次配息`" />
        <StatCard label="總報酬率" :value="pct(s.return_pct)" :tone="tone(s.return_pct)" hint="含已實現與股利" />
      </section>

      <AppCard :padded="false" class="overflow-hidden">
        <div class="flex flex-wrap items-center gap-3 px-5 py-4">
          <h2 class="m-0 mr-auto font-head text-base font-semibold">持倉</h2>
          <span class="num text-xs text-muted">平均成本法{{ s.oldest_price_date ? ` · 報價日 ${s.oldest_price_date}` : '' }} · 金額以 {{ s.base_currency }} 計</span>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full min-w-[900px] border-collapse text-sm">
            <thead>
              <tr class="lbl border-y border-line bg-panel2 text-left">
                <th class="px-5 py-2.5 font-normal">證券</th><th class="px-3 py-2.5 text-right font-normal">股數</th><th class="px-3 py-2.5 text-right font-normal">均價</th>
                <th class="px-3 py-2.5 text-right font-normal">現價</th><th class="px-3 py-2.5 text-right font-normal">市值</th><th class="px-3 py-2.5 text-right font-normal">未實現損益</th>
                <th class="px-3 py-2.5 text-right font-normal">報酬率</th><th class="w-36 px-3 py-2.5 font-normal">佔比</th><th class="w-14 px-3 py-2.5"><span class="sr-only">K 線圖</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in holdings" :key="h.account_id + h.security.id" class="border-b border-line last:border-b-0 hover:bg-panel2">
                <td class="px-5 py-2.5">
                  <button type="button" class="flex cursor-pointer items-center gap-2.5 border-0 bg-transparent p-0 text-left text-text" :aria-label="`新增 ${h.security.symbol} 的交易`"
                    @click="ui.openTrade({ preset: { account_id: h.account_id, security: h.security } })">
                    <Badge :tone="h.security.market === 'US' ? 'muted' : 'accent'">{{ h.security.market }}</Badge>
                    <span class="flex flex-col"><span class="num font-semibold">{{ h.security.symbol }}</span><span class="text-xs text-muted">{{ h.security.name }}<template v-if="account === 'all' && investAccounts.length > 1"> · {{ accounts.byId(h.account_id)?.name }}</template></span></span>
                  </button>
                </td>
                <td class="num px-3 py-2.5 text-right">{{ qty(h.quantity) }}</td>
                <td class="num px-3 py-2.5 text-right">{{ px(h.avg_cost, h.security.currency) }}</td>
                <td class="px-3 py-2.5 text-right">
                  <button type="button" class="ml-auto flex cursor-pointer flex-col items-end border-0 bg-transparent p-0 text-text" :aria-label="`輸入 ${h.security.symbol} 的報價`" @click="openPrice(h)">
                    <span class="num">{{ h.price ? px(h.price, h.security.currency) : '—' }}</span>
                    <span class="num text-[10px]" :class="priceTag(h).cls">{{ priceTag(h).text }}</span>
                  </button>
                </td>
                <td class="num px-3 py-2.5 text-right">{{ fmt(h.market_value_base) }}</td>
                <td class="num px-3 py-2.5 text-right" :class="toneCls(h.unrealized_base)">{{ fmt(h.unrealized_base, 'always') }}</td>
                <td class="num px-3 py-2.5 text-right" :class="toneCls(h.return_pct)">{{ pct(h.return_pct) }}</td>
                <td class="px-3 py-2.5">
                  <span class="flex items-center gap-2"><span class="h-1.5 flex-1 overflow-hidden rounded-full bg-panel2"><span class="block h-full bg-accent" :style="{ width: `${share(h)}%` }" /></span><span class="num w-12 text-right text-xs text-muted">{{ share(h).toFixed(1) }}%</span></span>
                </td>
                <td class="px-3 py-1.5 text-right">
                  <IconButton :label="`開啟 ${h.security.symbol} K 線圖`" @click="openChart(h)"><ChartCandlestick :size="16" aria-hidden="true" /></IconButton>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!holdings.length" class="m-0 px-5 py-8 text-center text-sm text-muted">目前沒有持股。按「新增投資交易」登錄第一筆買進。</p>
        <div v-for="h in needPrice" :key="'np' + h.security.id" class="flex flex-wrap items-center gap-2.5 border-t border-line bg-warn-soft px-5 py-3 text-sm">
          <CircleAlert :size="16" class="text-warn" aria-hidden="true" />
          <span class="mr-auto">{{ h.security.symbol }} 沒有自動報價來源，{{ h.price ? `目前用 ${shortDate(h.price_date)} ${h.manual_price ? '手動輸入' : ''}的價格計算` : '目前以成本計算市值' }}。</span>
          <AppButton variant="ghost" @click="openPrice(h)">輸入今日報價</AppButton>
        </div>
      </AppCard>

      <section class="flex flex-wrap items-start gap-4">
        <AppCard class="flex min-w-0 flex-[1_1_360px] flex-col gap-4">
          <h2 class="m-0 font-head text-base font-semibold">資產配置</h2>
          <p v-if="!holdings.length" class="m-0 text-sm text-muted">有持股後顯示。</p>
          <div v-for="g in allocation" :key="g.title" class="flex flex-col gap-2">
            <span class="lbl">{{ g.title }}</span>
            <div class="flex h-3 overflow-hidden rounded-full bg-panel2" role="img" :aria-label="`${g.title}：${g.parts.map(p => `${p.label} ${p.pct}%`).join('、')}`">
              <span v-for="(p, i) in g.parts" :key="p.key" class="block h-full" :style="{ width: `${p.pct}%`, background: COLORS[i % COLORS.length] }" />
            </div>
            <div class="num flex flex-wrap gap-x-4 gap-y-1 text-xs">
              <span v-for="(p, i) in g.parts" :key="p.key" class="flex items-center gap-1.5"><span class="size-2.5 rounded-sm" :style="{ background: COLORS[i % COLORS.length] }" />{{ p.label }} <span class="text-muted">{{ p.pct }}%</span></span>
            </div>
          </div>
        </AppCard>

        <AppCard :padded="false" class="flex min-w-0 flex-[2_1_480px] flex-col">
          <div class="flex items-center gap-3 px-5 pt-5 pb-2">
            <h2 class="m-0 mr-auto font-head text-base font-semibold">{{ showAll ? '投資交易' : '最近投資交易' }}</h2>
            <AppButton variant="ghost" :loading="busy === 'csv'" @click="exportCsv"><Download :size="16" aria-hidden="true" />匯出 CSV</AppButton>
            <button v-if="!showAll && trades.total > RECENT" type="button" class="min-h-11 cursor-pointer border-0 bg-transparent text-[13px] text-accent" @click="showAll = true">全部 →</button>
          </div>
          <button v-for="r in trades.items" :key="r.transaction.id" type="button"
            class="flex min-h-14 w-full cursor-pointer flex-wrap items-center gap-3 border-0 border-b border-line bg-transparent px-5 py-2.5 text-left text-text last:border-b-0 hover:bg-panel2 sm:flex-nowrap"
            @click="ui.openTrade({ id: r.transaction.id })">
            <span class="num w-12 text-xs text-muted">{{ shortDate(r.transaction.date) }}</span>
            <Badge :tone="TRADE_TONE[r.transaction.investment.trade_type]">{{ tradeLabel(r.transaction.investment.trade_type) }}</Badge>
            <span class="flex min-w-0 flex-1 flex-col gap-0.5">
              <span class="truncate">{{ r.security?.symbol }} {{ r.security?.name }}<template v-if="r.transaction.investment.quantity && ['buy', 'sell', 'stock_dividend', 'opening'].includes(r.transaction.investment.trade_type)"> × {{ qty(r.transaction.investment.quantity) }}</template></span>
              <span class="num truncate text-xs text-muted">{{ tradeMeta(r) }}</span>
            </span>
            <span class="flex flex-col items-end">
              <Amount v-if="r.transaction.entries[0]" :value="r.transaction.entries[0].amount" :currency="accounts.byId(r.transaction.entries[0].account_id)?.currency" sign="auto" class="text-sm" />
              <span v-else class="text-sm text-muted">—</span>
              <span class="text-[11px] text-muted">{{ settleText(r) }}</span>
            </span>
          </button>
          <p v-if="!trades.items.length" class="m-0 px-5 py-8 text-center text-sm text-muted">還沒有投資交易。</p>
        </AppCard>
      </section>
    </template>
    <AppCard v-else-if="!loaded" class="py-12 text-center text-muted">載入中…</AppCard>

    <ChartDialog v-model:open="chartDlg.open" :security="chartDlg.security" :account-id="chartDlg.accountId" />
    <AppDialog v-model:open="priceDlg.open" :title="`輸入 ${priceDlg.symbol} 報價`" description="手動報價只影響你自己的計算；同一天再輸入一次會覆蓋。" :width="420">
      <form id="price-form" class="grid gap-3 sm:grid-cols-2" novalidate @submit.prevent="savePrice">
        <DateField v-model="priceDlg.date" label="日期" :max="today()" required />
        <TextField v-model="priceDlg.close" :label="`收盤價 · ${priceDlg.currency}`" inputmode="decimal" mono align="right" :error="priceDlg.error" required />
      </form>
      <template #footer>
        <AppButton variant="ghost" class="ml-auto" @click="priceDlg.open = false">取消</AppButton>
        <AppButton type="submit" form="price-form" :loading="busy === 'price'">儲存</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChartCandlestick, CircleAlert, Download, Plus, RefreshCw, TrendingUp } from 'lucide-vue-next'
import Amount from '../components/ui/Amount.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import AppDialog from '../components/ui/AppDialog.vue'
import Badge from '../components/ui/Badge.vue'
import DateField from '../components/ui/DateField.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import IconButton from '../components/ui/IconButton.vue'
import ChartDialog from '../components/ChartDialog.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import SegmentedControl from '../components/ui/SegmentedControl.vue'
import StatCard from '../components/ui/StatCard.vue'
import TextField from '../components/ui/TextField.vue'
import api from '../services/api'
import { download } from '../services/download'
import { useAccountsStore } from '../stores/accounts'
import { useUiStore } from '../stores/ui'
import { toast } from '../stores/toast'
import { formatAmount } from '../utils/money'
import { shortDate, today } from '../utils/date'
import { assetTypeLabel, hasAutoQuote, marketLabel, tradeLabel } from '../utils/invest'

// 投資頁（SA FR-9、SD 5.1、示意圖 Investments）：?account= 切換投資帳戶
const RECENT = 8
const COLORS = ['var(--c1)', 'var(--c3)', 'var(--c2)', 'var(--c4)', 'var(--c5)', 'var(--c6)']
const TRADE_TONE = { buy: 'accent', sell: 'warn', cash_dividend: 'inc', stock_dividend: 'inc', split: 'muted', fee: 'exp', opening: 'muted' }
const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const accounts = useAccountsStore()
const account = ref(route.query.account || 'all')
const s = ref(null)
const holdings = ref([])
const trades = ref({ items: [], total: 0 })
const showAll = ref(false)
const loaded = ref(false)
const busy = ref('')
const year = today().slice(0, 4)
// K 線圖：「全部帳戶」時合併所有帳戶的持股算均價
const chartDlg = reactive({ open: false, security: null, accountId: null })
function openChart (h) {
  Object.assign(chartDlg, { open: true, security: h.security, accountId: account.value === 'all' ? null : h.account_id })
}
const priceDlg = reactive({ open: false, securityId: null, symbol: '', currency: 'TWD', date: today(), close: '', error: '' })

const investAccounts = computed(() => accounts.list.filter(a => a.kind === 'investment' && !a.archived_at))
const accountPills = computed(() => [{ value: 'all', label: '全部帳戶' }, ...investAccounts.value.map(a => ({ value: a.id, label: a.name }))])
const base = computed(() => s.value?.base_currency ?? 'TWD')
const fmt = (v, sign = 'auto') => formatAmount(v, base.value, { sign })
const pct = (v) => `${Number(v) > 0 ? '+' : Number(v) < 0 ? '−' : ''}${Math.abs(Number(v ?? 0)).toFixed(1)}%`
const tone = (v) => Number(v) > 0 ? 'inc' : Number(v) < 0 ? 'exp' : ''
const toneCls = (v) => Number(v) > 0 ? 'text-inc' : Number(v) < 0 ? 'text-exp' : ''
const qty = (v) => Number(v).toLocaleString('en-US', { maximumFractionDigits: 6 })
const px = (v, cur) => (cur !== base.value ? `${cur} ` : '') + Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
const total = computed(() => holdings.value.reduce((t, h) => t + Number(h.market_value_base), 0))
const share = (h) => total.value ? Number(h.market_value_base) / total.value * 100 : 0
const priceTag = (h) => {
  if (!h.price) return { text: '尚無報價', cls: 'text-warn' }
  const src = h.manual_price ? '手動' : h.price_source === 'twse' ? 'TWSE' : h.price_source === 'tpex' ? 'TPEx' : h.price_source
  return { text: `${shortDate(h.price_date)} ${src}`, cls: h.manual_price || !hasAutoQuote(h.security.market) ? 'text-warn' : 'text-muted' }
}
// 沒有自動報價來源、且報價不是今天的持股
const needPrice = computed(() => holdings.value.filter(h => !hasAutoQuote(h.security.market) && h.price_date !== today()))
const allocation = computed(() => {
  if (!s.value) return []
  const label = { market: marketLabel, asset_type: assetTypeLabel, currency: (v) => v }
  return [['market', '依市場'], ['asset_type', '依類型'], ['currency', '依幣別']].map(([k, title]) => ({
    title, parts: (s.value.allocation[k] ?? []).map(p => ({ key: p.key, label: label[k](p.key), pct: Number(p.percentage) }))
  })).filter(g => g.parts.length)
})
const tradeMeta = (r) => {
  const iv = r.transaction.investment
  const parts = []
  const cur = r.security?.currency
  if (iv.trade_type === 'buy' || iv.trade_type === 'sell') parts.push(`@ ${px(iv.price, cur)}`)
  if (iv.trade_type === 'opening') {
    parts.push(`均價 ${px(iv.price, cur)}`)
    if (iv.principal != null) parts.push(`投入本金 ${formatAmount(iv.principal, cur)}`)
  }
  if (iv.trade_type === 'cash_dividend') parts.push(`每股 ${px(iv.price, cur)} × ${qty(iv.quantity)}`)
  if (iv.trade_type === 'split') parts.push(`1 股變 ${qty(iv.quantity)} 股`)
  if (Number(iv.fee)) parts.push(`手續費 ${formatAmount(iv.fee, cur)}`)
  if (Number(iv.tax)) parts.push(`稅 ${formatAmount(iv.tax, cur)}`)
  if (r.realized != null) parts.push(`已實現 ${formatAmount(r.realized, cur, { sign: 'always' })}`)
  return parts.join(' · ')
}
const settleText = (r) => {
  const e = r.transaction.entries[0]
  if (!e) return '沒有現金流動'
  const name = accounts.byId(e.account_id)?.name
  return r.transaction.investment.trade_type === 'cash_dividend' ? `入帳：${name} · 投資損益` : `交割：${name}`
}

function setAccount (v) {
  account.value = v
  router.replace({ query: v === 'all' ? {} : { account: v } })
}

async function load () {
  const params = account.value === 'all' ? {} : { account_id: account.value }
  const [sum, hs, tr] = await Promise.all([
    api.get('/investments/summary', { params }),
    api.get('/investments/holdings', { params }),
    api.get('/investments/trades', { params: { ...params, size: showAll.value ? 200 : RECENT } }),
    accounts.ensure()
  ])
  s.value = sum.data
  holdings.value = hs.data
  trades.value = tr.data
  loaded.value = true
}

async function refreshQuotes () {
  busy.value = 'quotes'
  try {
    const { data } = await api.post('/investments/prices/refresh')
    toast.success(data.message)
    await load()
  } finally {
    busy.value = ''
  }
}

function openPrice (h) {
  Object.assign(priceDlg, { open: true, securityId: h.security.id, symbol: h.security.symbol, currency: h.security.currency, date: today(), close: h.price ? String(Number(h.price)) : '', error: '' })
}
async function savePrice () {
  const close = String(priceDlg.close).replace(/,/g, '').trim()
  priceDlg.error = /^\d+(\.\d{1,6})?$/.test(close) && Number(close) > 0 ? '' : '請輸入大於 0 的數字（最多 6 位小數）'
  if (priceDlg.error) return
  busy.value = 'price'
  try {
    await api.put('/investments/prices', { security_id: priceDlg.securityId, date: priceDlg.date, close })
    toast.success(`已輸入 ${priceDlg.symbol} 的報價`)
    priceDlg.open = false
    await load()
  } finally {
    busy.value = ''
  }
}

async function exportCsv () {
  busy.value = 'csv'
  try {
    await download('/export/trades', `do-accounts-投資交易-${today()}.csv`, account.value === 'all' ? {} : { account_id: account.value })
  } finally {
    busy.value = ''
  }
}

onMounted(async () => {
  await accounts.ensure()
  if (!investAccounts.value.length) { loaded.value = true; return }
  await load()
})
watch([account, showAll], load)
watch(() => ui.ledgerVersion, () => investAccounts.value.length && load())
</script>
