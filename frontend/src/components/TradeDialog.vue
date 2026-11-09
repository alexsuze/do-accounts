<template>
  <AppDialog :open="!!ui.tradeDialog" :title="editing ? '編輯投資交易' : '新增投資交易'" :width="660" no-auto-focus @update:open="v => !v && close()">
    <form id="trade-form" class="flex flex-col gap-4" novalidate @submit.prevent="save">
      <div role="tablist" aria-label="交易類型" class="grid grid-cols-4 gap-1.5 rounded-xl border border-line bg-panel2 p-1 sm:grid-cols-7">
        <button v-for="t in TRADE_TYPES" :key="t.value" type="button" role="tab" :aria-selected="form.trade_type === t.value"
          class="h-10 rounded-[9px] border text-[13px] font-semibold transition-colors"
          :class="form.trade_type === t.value ? 'border-accent bg-panel text-accent' : 'border-transparent text-muted hover:text-text'"
          @click="form.trade_type = t.value">{{ t.label }}</button>
      </div>

      <div class="grid gap-3 sm:grid-cols-2">
        <SelectField v-model="form.account_id" label="投資帳戶" :options="investmentOpts" :error="errors.account_id" required
          :hint="investmentOpts.length ? '' : '請先在帳戶頁建立「投資」類型的帳戶'" />
        <DateField v-model="form.date" label="日期" :error="errors.date" required />
      </div>

      <!-- 證券 -->
      <div class="flex flex-col gap-1.5">
        <span class="text-[13px] text-muted">證券 <span class="text-exp" aria-hidden="true">*</span></span>
        <div v-if="security" class="flex min-h-11 flex-wrap items-center gap-2.5 rounded-[10px] border border-accent bg-accent-soft px-3.5 py-2">
          <Badge :tone="security.market === 'US' ? 'muted' : 'accent'">{{ security.market }}</Badge>
          <span class="num font-semibold">{{ security.symbol }}</span>
          <span class="mr-auto">{{ security.name }} · {{ assetTypeLabel(security.asset_type) }} · {{ security.currency }}</span>
          <button v-if="!editing" type="button" class="min-h-9 cursor-pointer rounded-lg border-0 bg-transparent px-2 text-sm text-accent underline" @click="clearSecurity">更換</button>
        </div>
        <div v-else class="relative">
          <span class="flex h-11 items-center gap-2 rounded-[10px] border border-line bg-panel2 px-3 focus-within:border-accent">
            <Search :size="16" class="text-muted" aria-hidden="true" />
            <input v-model="query" class="num min-w-0 flex-1 border-0 bg-transparent text-text outline-none" aria-label="搜尋證券代號或名稱" placeholder="輸入代號或名稱，例如 0050、2330、VOO" @input="search">
          </span>
          <ul v-if="results.length" class="absolute inset-x-0 top-full z-10 mt-1 max-h-64 overflow-y-auto rounded-[10px] border border-line bg-panel2 p-0 shadow-xl" aria-label="搜尋結果">
            <li v-for="s in results" :key="s.id" class="list-none">
              <button type="button" class="flex min-h-11 w-full items-center gap-2.5 px-3 py-2 text-left text-sm text-text hover:bg-accent-soft" @click="pick(s)">
                <span class="num w-10 text-xs text-muted">{{ s.market }}</span><span class="num font-semibold">{{ s.symbol }}</span><span class="truncate">{{ s.name }}</span>
              </button>
            </li>
          </ul>
          <p v-if="errors.security_id" role="alert" class="m-0 mt-1 text-xs text-exp">{{ errors.security_id }}</p>
          <button v-if="query.trim() && !searching && !results.length && !newSec.open" type="button" class="mt-2 cursor-pointer border-0 bg-transparent p-0 text-sm text-accent underline"
            @click="openNewSecurity">找不到「{{ query.trim() }}」？手動新增證券</button>
          <div v-if="newSec.open" class="mt-2 grid gap-3 rounded-[12px] border border-dashed border-line p-3 sm:grid-cols-2">
            <SelectField v-model="newSec.market" label="市場" :options="MARKETS" />
            <TextField v-model="newSec.symbol" label="代號" mono />
            <TextField v-model="newSec.name" label="名稱" :maxlength="60" />
            <SelectField v-model="newSec.asset_type" label="類型" :options="ASSET_TYPES" />
            <SelectField v-model="newSec.currency" label="幣別" :options="CURRENCY_OPTIONS" />
            <div class="flex items-end gap-2"><AppButton variant="ghost" @click="newSec.open = false">取消</AppButton><AppButton :loading="busy === 'sec'" @click="createSecurity">新增證券</AppButton></div>
          </div>
        </div>
        <span v-if="security && holdingText" class="num text-xs text-muted">{{ holdingText }}</span>
      </div>

      <!-- 買進／賣出 -->
      <template v-if="isTrade">
        <div class="grid gap-3 sm:grid-cols-2">
          <TextField v-model="form.quantity" label="股數" inputmode="decimal" mono align="right" :error="errors.quantity || (preview?.oversell ? `賣出股數超過持有股數（持有 ${fmtQty(holding?.quantity)} 股）` : '')" required />
          <TextField v-model="form.price" :label="`成交價 · ${cur}`" inputmode="decimal" mono align="right" :error="errors.price" required />
        </div>
        <div class="grid gap-3 sm:grid-cols-3">
          <SelectField v-if="tw" v-model="discount" label="手續費折扣" :options="DISCOUNTS" />
          <TextField v-model="form.fee" :label="tw ? '手續費（自動帶入）' : `手續費 · ${cur}`" inputmode="decimal" mono align="right" :error="errors.fee" @update:model-value="feeTouched = true" />
          <TextField v-model="form.tax" :label="tw && form.trade_type === 'sell' ? '證交稅（自動帶入）' : `稅 · ${cur}`" inputmode="decimal" mono align="right" :error="errors.tax" @update:model-value="taxTouched = true" />
        </div>
      </template>
      <template v-else-if="form.trade_type === 'opening'">
        <span class="text-sm text-muted">開始記帳前就持有的股票：只登錄股數、均價與投入本金，不會動到現金。日期可填投資帳戶的開戶日期。</span>
        <div class="grid gap-3 sm:grid-cols-3">
          <TextField v-model="form.quantity" label="股數" inputmode="decimal" mono align="right" :error="errors.quantity" required />
          <TextField v-model="form.price" :label="`均價 · ${cur}`" inputmode="decimal" mono align="right" :error="errors.price" required />
          <TextField v-model="form.principal" :label="`投入本金 · ${cur}`" inputmode="decimal" mono align="right" :error="errors.principal"
            :placeholder="openingCost ? fmtMoney(openingCost) : '選填'" hint="空白＝股數 × 均價" />
        </div>
      </template>
      <div v-else-if="form.trade_type === 'cash_dividend'" class="grid gap-3 sm:grid-cols-3">
        <TextField v-model="form.price" :label="`每股配息 · ${cur}`" inputmode="decimal" mono align="right" :error="errors.price" required />
        <TextField v-model="form.quantity" label="參與配息股數" inputmode="decimal" mono align="right" :error="errors.quantity" required />
        <TextField v-model="form.fee" label="匯費／二代健保" inputmode="decimal" mono align="right" :error="errors.fee" />
      </div>
      <div v-else-if="OTHER[form.trade_type]" class="flex flex-col gap-2">
        <span class="text-sm text-muted">{{ OTHER[form.trade_type].hint }}</span>
        <TextField v-if="form.trade_type === 'fee'" v-model="form.fee" :label="`費用金額 · ${cur}`" inputmode="decimal" mono align="right" :error="errors.fee" required />
        <TextField v-else v-model="form.quantity" :label="OTHER[form.trade_type].label" inputmode="decimal" mono align="right" :error="errors.quantity" required />
      </div>

      <!-- 交割 -->
      <div v-if="hasCash" class="grid gap-3 sm:grid-cols-2">
        <SelectField v-model="form.settlement_account_id" label="交割帳戶" :options="settleOpts" :error="errors.settlement_account_id" />
        <MoneyInput v-if="crossCurrency" v-model="form.settlement_amount" :currency="settleAccount.currency"
          :label="`實際${(preview?.cash ?? 0) < 0 ? '扣款' : '入帳'}金額 · ${settleAccount.currency}`" :error="errors.settlement_amount" required />
      </div>
      <TextField v-model="form.note" label="備註" :maxlength="500" placeholder="選填" />

      <div v-if="summary.length" class="num flex flex-col gap-1.5 rounded-[12px] border border-line bg-panel2 px-4 py-3 text-sm">
        <div v-for="s in summary" :key="s.k" class="flex justify-between gap-3" :class="s.total && 'border-t border-line pt-2 text-base font-semibold'">
          <span class="text-muted">{{ s.k }}</span><span :class="s.cls">{{ s.v }}</span>
        </div>
      </div>
    </form>
    <template #footer>
      <AppButton v-if="editing" variant="danger" @click="confirmDelete = true"><Trash2 :size="16" aria-hidden="true" />刪除</AppButton>
      <span class="mr-auto text-xs text-muted">{{ footNote }}</span>
      <AppButton variant="ghost" @click="close">取消</AppButton>
      <AppButton type="submit" form="trade-form" :loading="busy === 'save'" :disabled="!!preview?.oversell">儲存</AppButton>
    </template>
  </AppDialog>
  <ConfirmDialog v-model:open="confirmDelete" danger title="刪除這筆投資交易？" description="持倉與損益會重新計算；如果之後有賣出因此超賣，系統會拒絕刪除。" confirm-text="刪除" @confirm="remove" />
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Search, Trash2 } from 'lucide-vue-next'
import AppButton from './ui/AppButton.vue'
import AppDialog from './ui/AppDialog.vue'
import Badge from './ui/Badge.vue'
import ConfirmDialog from './ui/ConfirmDialog.vue'
import DateField from './ui/DateField.vue'
import MoneyInput from './ui/MoneyInput.vue'
import SelectField from './ui/SelectField.vue'
import TextField from './ui/TextField.vue'
import api from '../services/api'
import { useAccountsStore } from '../stores/accounts'
import { useUiStore } from '../stores/ui'
import { toast } from '../stores/toast'
import { accountOptions } from './options'
import { CURRENCY_OPTIONS } from '../utils/labels'
import { digitsOf, formatAmount } from '../utils/money'
import { today } from '../utils/date'
import { toAmountString } from '../utils/expr'
import { ASSET_TYPES, MARKETS, TRADE_TYPES, afterTrade, assetTypeLabel, hasAutoQuote, settlement, tradeLabel, twFee, twTax } from '../utils/invest'

// 投資交易視窗（SA FR-9.2、9.3、UC-08、示意圖 TradeDialog）：存成一份 kind = investment 的交易，交割現金自動入帳
const DISCOUNTS = [{ value: '1', label: '無折扣' }, { value: '0.6', label: '6 折' }, { value: '0.5', label: '5 折' }, { value: '0.28', label: '2.8 折' }]
const OTHER = {
  stock_dividend: { hint: '配發股票，不影響現金；成本不變、均價下降。', label: '配發股數' },
  split: { hint: '股票分割（或合併）時，依比例調整股數。', label: '分割比例（1 股變幾股）' },
  fee: { hint: '帳戶管理費、保管費等與這檔證券相關的費用（計入支出「手續費」）。', label: '費用金額' }
}
const ui = useUiStore()
const accounts = useAccountsStore()
const form = reactive({})
const errors = reactive({})
const security = ref(null)
const query = ref('')
const results = ref([])
const searching = ref(false)
const holdings = ref([])
const editing = ref(null)
const busy = ref('')
const discount = ref('1')
const feeTouched = ref(false)
const taxTouched = ref(false)
const confirmDelete = ref(false)
const newSec = reactive({ open: false, market: 'US', symbol: '', name: '', asset_type: 'stock', currency: 'USD' })

const investmentOpts = computed(() => accountOptions(accounts.list.filter(a => a.kind === 'investment'), { keep: [form.account_id] }))
const settleOpts = computed(() => accountOptions(accounts.list, { keep: [form.settlement_account_id] }))
const settleAccount = computed(() => accounts.byId(form.settlement_account_id || form.account_id))
const cur = computed(() => security.value?.currency ?? 'TWD')
const tw = computed(() => hasAutoQuote(security.value?.market))
const isTrade = computed(() => form.trade_type === 'buy' || form.trade_type === 'sell')
const hasCash = computed(() => ['buy', 'sell', 'cash_dividend', 'fee'].includes(form.trade_type))
const crossCurrency = computed(() => hasCash.value && security.value && settleAccount.value && settleAccount.value.currency !== security.value.currency)
const holding = computed(() => holdings.value.find(h => h.account_id === form.account_id && h.security.id === security.value?.id) ?? null)
const fmtQty = (v) => Number(v ?? 0).toLocaleString('en-US', { maximumFractionDigits: 6 })
const fmtPx = (v) => Number(v ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
const holdingText = computed(() => {
  const h = holding.value
  if (!h) return '這個帳戶目前沒有持有'
  return `目前持有 ${fmtQty(h.quantity)} 股 · 均價 ${fmtPx(h.avg_cost)}${h.price ? ` · ${h.price_date.slice(5).replace('-', '/')} 收盤 ${fmtPx(h.price)}` : ''}`
})

// 編輯時，原本這筆交易已經算在持股裡：預覽要先扣掉（只有新增時才能直接用目前持股）
const base = computed(() => editing.value ? null : holding.value)
const preview = computed(() => {
  if (!security.value) return null
  const cash = settlement(form.trade_type, form)
  const after = afterTrade(form.trade_type, base.value ? { quantity: base.value.quantity, cost: base.value.cost } : null, form)
  return { ...after, cash: cash?.cash ?? null, gross: cash?.gross ?? 0, oversell: !editing.value && after.oversell }
})
const money = (v, c = cur.value, sign = 'auto') => formatAmount(v, c, { sign })
const fmtMoney = (v) => formatAmount(v, cur.value)
const openingCost = computed(() => Number(form.quantity || 0) * Number(String(form.price || 0).replace(/,/g, '')))
const summary = computed(() => {
  const p = preview.value
  if (!p) return []
  const out = []
  const t = form.trade_type
  if (isTrade.value || t === 'cash_dividend') out.push({ k: t === 'cash_dividend' ? '股利' : '成交金額', v: money(p.gross) })
  if (isTrade.value && (Number(form.fee) || Number(form.tax))) out.push({ k: '手續費＋稅', v: money((t === 'buy' ? 1 : -1) * (Number(form.fee || 0) + Number(form.tax || 0)), cur.value, 'always') })
  if (p.cash != null) {
    const label = p.cash < 0 ? '交割金額（從交割帳戶扣款）' : '入帳金額'
    const v = crossCurrency.value ? (form.settlement_amount ? money((p.cash < 0 ? -1 : 1) * Number(form.settlement_amount), settleAccount.value.currency, 'always') : '請填實際金額') : money(p.cash, cur.value, 'always')
    out.push({ k: label, v, total: true })
  }
  if (t === 'opening') {
    out.push({ k: '成本（股數 × 均價）', v: money(openingCost.value) })
    const pr = num(form.principal)
    out.push({ k: '投入本金（報酬率的分母）', v: money(pr ?? openingCost.value), total: true })
    if (pr != null && Number(pr) !== openingCost.value) {
      const diff = openingCost.value - Number(pr)
      out.push({ k: '開始記帳前的損益（計入報酬率）', v: money(diff, cur.value, 'always'), cls: diff >= 0 ? 'text-inc' : 'text-exp' })
    }
  }
  if (t === 'sell' && p.realized != null && !editing.value && holding.value) {
    out.push({ k: '已實現損益（平均成本法）', v: money(p.realized, cur.value, 'always'), cls: p.realized >= 0 ? 'text-inc' : 'text-exp' })
  }
  if (!editing.value && ['buy', 'sell', 'stock_dividend', 'split', 'opening'].includes(t) && !p.oversell) {
    out.push({ k: '交易後持股', v: `${fmtQty(holding.value?.quantity ?? 0)} → ${fmtQty(p.quantity)} 股 · 均價 ${fmtPx(holding.value?.avg_cost ?? 0)} → ${fmtPx(p.avg)}` })
  }
  return out
})
const footNote = computed(() => {
  if (preview.value?.oversell) return '修正錯誤後才能儲存'
  if (form.trade_type === 'cash_dividend') return '計入收入分類「投資損益」'
  if (form.trade_type === 'fee') return '計入支出分類「手續費」'
  if (form.trade_type === 'opening') return '不影響現金；報酬率以投入本金計算'
  if (!hasCash.value) return '沒有現金流動'
  return '儲存後同時產生交割帳戶的現金分錄'
})

// 台股：手續費與證交稅自動帶入（使用者改過就不再覆蓋）
watch(() => [form.quantity, form.price, form.trade_type, discount.value, security.value?.id], () => {
  if (!isTrade.value || !tw.value) return
  const gross = Number(form.quantity || 0) * Number(form.price || 0)
  if (!feeTouched.value) form.fee = String(twFee(gross, security.value.market, Number(discount.value)))
  if (!taxTouched.value) form.tax = form.trade_type === 'sell' ? String(twTax(gross, security.value.market, security.value.asset_type)) : '0'
})
// 現金股利：參與股數預設為目前持股
watch(() => [form.trade_type, holding.value], () => {
  if (form.trade_type === 'cash_dividend' && !editing.value && !form.quantity && holding.value) form.quantity = String(Number(holding.value.quantity))
})

watch(() => ui.tradeDialog?.key, async (k) => {
  if (!k) return
  const d = ui.tradeDialog
  Object.keys(errors).forEach(x => delete errors[x])
  security.value = null
  query.value = ''
  results.value = []
  newSec.open = false
  feeTouched.value = false
  taxTouched.value = false
  discount.value = '1'
  editing.value = null
  await accounts.ensure()
  const firstInvest = accounts.list.find(a => a.kind === 'investment' && !a.archived_at)
  Object.assign(form, {
    trade_type: d.preset?.trade_type ?? 'buy', account_id: d.preset?.account_id ?? firstInvest?.id ?? null, date: today(),
    quantity: '', price: '', fee: '', tax: '', settlement_account_id: null, settlement_amount: '', note: '', principal: ''
  })
  form.settlement_account_id = form.account_id
  holdings.value = (await api.get('/investments/holdings', { silent: true }).catch(() => ({ data: [] }))).data
  if (d.preset?.security) security.value = d.preset.security
  if (d.id) {
    const { data: t } = await api.get(`/investments/trades/${d.id}`)
    editing.value = t
    const iv = t.investment
    security.value = (await api.get(`/investments/securities/${iv.security_id}`)).data
    feeTouched.value = true
    taxTouched.value = true
    const settle = t.entries[0]
    Object.assign(form, {
      trade_type: iv.trade_type, account_id: iv.account_id, date: t.date,
      quantity: iv.quantity != null ? String(Number(iv.quantity)) : '', price: iv.price != null ? String(Number(iv.price)) : '',
      fee: iv.fee != null ? String(Number(iv.fee)) : '', tax: iv.tax != null ? String(Number(iv.tax)) : '',
      settlement_account_id: settle ? settle.account_id : iv.account_id,
      settlement_amount: settle ? String(Math.abs(Number(settle.amount))) : '', note: t.note ?? '',
      principal: iv.principal != null ? String(Number(iv.principal)) : ''
    })
  }
})

let timer = null
function search () {
  clearTimeout(timer)
  const q = query.value.trim()
  if (!q) { results.value = []; return }
  searching.value = true
  timer = setTimeout(async () => {
    try {
      const { data } = await api.get('/investments/securities', { params: { q }, silent: true })
      if (q === query.value.trim()) results.value = data
    } finally {
      searching.value = false
    }
  }, 250)
}
function pick (s) {
  security.value = s
  results.value = []
  delete errors.security_id
}
function clearSecurity () {
  security.value = null
  query.value = ''
}
function openNewSecurity () {
  Object.assign(newSec, { open: true, market: /^\d/.test(query.value.trim()) ? 'TW' : 'US', symbol: query.value.trim().toUpperCase(), name: '' })
  newSec.currency = newSec.market === 'US' ? 'USD' : 'TWD'
}
// 交割帳戶預設跟著投資帳戶（使用者另外選過就不動）
watch(() => form.account_id, (now, before) => {
  if (!form.settlement_account_id || form.settlement_account_id === before) form.settlement_account_id = now
})
watch(() => newSec.market, (m) => { newSec.currency = m === 'US' ? 'USD' : 'TWD' })
async function createSecurity () {
  busy.value = 'sec'
  try {
    const { data } = await api.post('/investments/securities', { ...newSec })
    pick(data)
    newSec.open = false
  } finally {
    busy.value = ''
  }
}

function num (v) {
  const s = String(v ?? '').replace(/,/g, '').trim()
  return s === '' ? null : s
}

async function save () {
  Object.keys(errors).forEach(x => delete errors[x])
  if (!form.account_id) errors.account_id = '請選擇投資帳戶'
  if (!security.value) errors.security_id = '請選擇證券'
  let settlementAmount = null
  if (crossCurrency.value) {
    try { settlementAmount = toAmountString(form.settlement_amount, digitsOf(settleAccount.value.currency)) } catch (e) { errors.settlement_amount = e.message }
    if (!settlementAmount) errors.settlement_amount = '請填實際金額'
  }
  if (Object.keys(errors).length) return
  const body = {
    trade_type: form.trade_type, account_id: form.account_id, security_id: security.value.id, date: form.date,
    quantity: num(form.quantity), price: num(form.price), fee: num(form.fee), tax: num(form.tax),
    settlement_account_id: hasCash.value ? (form.settlement_account_id || form.account_id) : null,
    settlement_amount: settlementAmount, note: form.note, version: editing.value?.version ?? null,
    principal: form.trade_type === 'opening' ? num(form.principal) : null
  }
  busy.value = 'save'
  try {
    if (editing.value) await api.put(`/investments/trades/${editing.value.id}`, body)
    else await api.post('/investments/trades', body)
    toast.success(`已${editing.value ? '更新' : '新增'}${tradeLabel(form.trade_type)} ${security.value.symbol}`)
    ui.ledgerChanged()
    close()
  } finally {
    busy.value = ''
  }
}

async function remove () {
  await api.delete(`/investments/trades/${editing.value.id}`)
  toast.success('已刪除投資交易')
  ui.ledgerChanged()
  close()
}

function close () {
  ui.closeTrade()
}
</script>
