<template>
  <AppDialog :open="!!ui.txDialog" :title="editing ? '編輯交易' : '記帳'" :width="620" no-auto-focus @update:open="v => !v && close()">
    <form id="tx-form" class="flex flex-col gap-4" novalidate @submit.prevent="save" @keydown.ctrl.enter.prevent="save" @keydown.meta.enter.prevent="save">
      <div role="tablist" aria-label="交易類型" class="grid grid-cols-3 gap-1.5 rounded-xl border border-line bg-panel2 p-1">
        <button v-for="k in TX_KINDS" :key="k.value" type="button" role="tab" :aria-selected="form.kind === k.value"
          class="h-10 rounded-[9px] border text-[15px] font-semibold transition-colors"
          :class="form.kind === k.value ? `bg-panel ${tone[k.value]}` : 'border-transparent text-muted hover:text-text'"
          @click="setKind(k.value)">{{ k.label }}</button>
      </div>

      <template v-if="form.kind !== 'transfer'">
        <MoneyInput ref="amountEl" v-model="form.amount" large :currency="fromAccount?.currency || 'TWD'" :label="`金額 · ${fromAccount?.currency || 'TWD'}`" :error="errors.amount" required />
        <div class="relative">
          <TextField v-model="form.title" label="項目" :maxlength="100" placeholder="例如：午餐 便當" :error="errors.title" required @blur="hideSuggest" />
          <ul v-if="suggestions.length" class="absolute inset-x-0 top-full z-10 mt-1 overflow-hidden rounded-[10px] border border-line bg-panel2 p-0 shadow-xl" aria-label="常用項目">
            <li v-for="s in suggestions" :key="s.title" class="list-none">
              <button type="button" class="flex min-h-11 w-full items-center gap-2.5 px-3 py-2 text-left text-sm text-text hover:bg-accent-soft" @mousedown.prevent="applySuggestion(s)">
                <span>{{ s.title }}</span>
                <span class="mr-auto truncate text-xs text-muted">{{ categories.path(s.category_id) }} · {{ accounts.byId(s.account_id)?.name }}</span>
                <span class="num text-[11px] text-muted">×{{ s.count }}</span>
              </button>
            </li>
          </ul>
        </div>
        <div class="grid gap-3 sm:grid-cols-2">
          <SelectField v-model="form.category_id" label="分類" :options="categoryOpts" :error="errors.category_id" required />
          <SelectField v-model="form.account_id" label="帳戶" :options="accountOpts" :error="errors.account_id" required />
        </div>
      </template>

      <template v-else>
        <div class="grid gap-3 sm:grid-cols-2">
          <SelectField v-model="form.account_id" label="轉出帳戶" :options="accountOpts" :error="errors.account_id" required />
          <SelectField v-model="form.to_account_id" label="轉入帳戶" :options="accountOpts" :error="errors.to_account_id" required />
          <MoneyInput v-model="form.amount" :currency="fromAccount?.currency || 'TWD'" :label="`轉出金額 · ${fromAccount?.currency || '—'}`" :error="errors.amount" required />
          <MoneyInput v-if="crossCurrency" v-model="form.to_amount" :currency="toAccount.currency" :label="`轉入金額 · ${toAccount.currency}`" :error="errors.to_amount" required />
        </div>
        <p v-if="crossCurrency" class="num m-0 flex flex-wrap gap-x-4 gap-y-1 rounded-[10px] border border-dashed border-line px-3.5 py-2.5 text-[13px] text-muted">
          <span>跨幣別轉帳</span><span v-if="rateText" class="text-accent">{{ rateText }}</span>
        </p>
        <div class="grid gap-3 sm:grid-cols-2">
          <MoneyInput v-model="form.fee" :currency="fromAccount?.currency || 'TWD'" label="手續費（選填，記為支出）" :error="errors.fee" />
          <TextField v-model="form.title" label="項目（選填）" :maxlength="100" placeholder="轉帳" />
        </div>
      </template>

      <div class="grid gap-3 sm:grid-cols-2">
        <DateField v-model="form.date" label="日期" :min="minDate" :error="errors.date" :hint="minDate ? `不可早於開戶日期 ${minDate}` : ''" required />
        <TextField v-model="form.tags" label="標籤" placeholder="#出差 #聚餐" hint="以空白分隔，最多 10 個" />
      </div>
      <TextField v-model="form.note" label="備註" :maxlength="500" placeholder="選填" />
    </form>

    <template #footer>
      <label v-if="!editing" class="mr-auto flex min-h-11 items-center gap-2 text-sm">
        <input v-model="continuous" type="checkbox" class="size-[18px] accent-[var(--accent)]">連續記帳（儲存後清空、不關閉）
      </label>
      <template v-else>
        <AppButton variant="danger" class="mr-0" @click="confirmDelete = true"><Trash2 :size="16" aria-hidden="true" />刪除</AppButton>
        <AppButton variant="ghost" class="mr-auto" @click="copyAsNew"><Copy :size="16" aria-hidden="true" />複製為新交易</AppButton>
      </template>
      <AppButton variant="ghost" @click="close">取消</AppButton>
      <AppButton type="submit" form="tx-form" :loading="saving">儲存 <span class="num text-[11px] opacity-70">Ctrl+Enter</span></AppButton>
    </template>
  </AppDialog>

  <ConfirmDialog v-model:open="confirmDelete" danger title="刪除這筆交易？" :description="`「${form.title}」刪除後無法復原，帳戶餘額會跟著更新。`"
    confirm-text="刪除" @confirm="remove" />
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Copy, Trash2 } from 'lucide-vue-next'
import AppButton from './ui/AppButton.vue'
import AppDialog from './ui/AppDialog.vue'
import ConfirmDialog from './ui/ConfirmDialog.vue'
import DateField from './ui/DateField.vue'
import MoneyInput from './ui/MoneyInput.vue'
import SelectField from './ui/SelectField.vue'
import TextField from './ui/TextField.vue'
import { accountOptions, categoryOptions } from './options'
import api from '../services/api'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { useUiStore } from '../stores/ui'
import { toast } from '../stores/toast'
import { TX_KINDS } from '../utils/labels'
import { today } from '../utils/date'
import { digitsOf } from '../utils/money'
import { evaluate, toAmountString } from '../utils/expr'

// 記帳視窗（SA UC-02、FR-3.1～3.8）。開關由 ui.txDialog 控制，任何頁面都能開
const LAST_ACCOUNT = 'do-accounts:last-account'
const ui = useUiStore()
const accounts = useAccountsStore()
const categories = useCategoriesStore()
const tone = { expense: 'border-exp text-exp', income: 'border-inc text-inc', transfer: 'border-accent text-accent' }

const empty = () => ({ kind: 'expense', date: today(), title: '', note: '', tags: '', category_id: null, account_id: null, amount: '', to_account_id: null, to_amount: '', fee: '' })
const form = reactive(empty())
const errors = reactive({})
const editing = ref(null)
const saving = ref(false)
const continuous = ref(false)
const confirmDelete = ref(false)
const suggestions = ref([])
const amountEl = ref(null)

const fromAccount = computed(() => accounts.byId(form.account_id))
const toAccount = computed(() => accounts.byId(form.to_account_id))
const crossCurrency = computed(() => form.kind === 'transfer' && fromAccount.value && toAccount.value && fromAccount.value.currency !== toAccount.value.currency)
const keep = computed(() => [form.account_id, form.to_account_id].filter(Boolean))
const accountOpts = computed(() => accountOptions(accounts.list, { keep: keep.value }))
const categoryOpts = computed(() => categoryOptions(categories.tree(form.kind)))
const minDate = computed(() => [fromAccount.value?.opening_date, form.kind === 'transfer' ? toAccount.value?.opening_date : null].filter(Boolean).sort().at(-1) || undefined)
const rateText = computed(() => {
  try {
    const a = evaluate(form.amount); const b = evaluate(form.to_amount)
    if (!a || !b || b.isZero()) return ''
    const [big, small, bigCur, smallCur] = a.gte(b) ? [a, b, fromAccount.value.currency, toAccount.value.currency] : [b, a, toAccount.value.currency, fromAccount.value.currency]
    return `實際匯率 1 ${smallCur} = ${big.div(small).toDecimalPlaces(4).toString()} ${bigCur}`
  } catch { return '' }
})

// ---- 開啟時填入資料 ----
watch(() => ui.txDialog?.key, async (key) => {
  if (!key) return
  await Promise.all([accounts.ensure(), categories.ensure()])
  const { transaction, preset } = ui.txDialog
  Object.assign(form, empty())
  clearErrors()
  suggestions.value = []
  editing.value = transaction
  if (transaction) Object.assign(form, fromTransaction(transaction))
  else {
    form.account_id = defaultAccount()
    if (preset) Object.assign(form, preset)
  }
  await nextTick()
  amountEl.value?.focus()
})

function fromTransaction (t) {
  const e = (role) => t.entries.find(x => x.role === role)
  const main = e('main') ?? e('transfer_out')
  const fee = e('fee')
  return {
    kind: t.kind,
    date: t.date,
    title: t.kind === 'transfer' && t.title === '轉帳' ? '' : t.title,
    note: t.note ?? '',
    tags: (t.tags ?? []).map(x => '#' + x).join(' '),
    category_id: t.category_id,
    account_id: main?.account_id ?? null,
    amount: main ? String(Math.abs(Number(main.amount))) : '',
    to_account_id: e('transfer_in')?.account_id ?? null,
    to_amount: e('transfer_in') ? String(e('transfer_in').amount) : '',
    fee: fee ? String(-Number(fee.amount)) : ''
  }
}

function defaultAccount () {
  let last = null
  try { last = localStorage.getItem(LAST_ACCOUNT) } catch { /* 忽略 */ }
  const usable = accounts.active
  return usable.find(a => a.id === last)?.id ?? usable[0]?.id ?? null
}

function setKind (k) {
  if (form.kind === k) return
  form.kind = k
  // 分類與類型不符就清掉（收入／支出分類不同）
  if (form.category_id && categories.byId(form.category_id)?.kind !== k) form.category_id = null
  clearErrors()
}

// ---- 常用項目建議（SA FR-3.7） ----
let timer
watch(() => form.title, (q) => {
  clearTimeout(timer)
  if (editing.value || form.kind === 'transfer' || !q?.trim()) { suggestions.value = []; return }
  timer = setTimeout(async () => {
    try {
      const { data } = await api.get('/transactions/suggest', { params: { q: q.trim() }, silent: true })
      suggestions.value = data.filter(s => s.title !== q.trim() || s.count > 0).slice(0, 4)
    } catch { suggestions.value = [] }
  }, 250)
})
function applySuggestion (s) {
  form.title = s.title
  if (s.kind !== form.kind && s.kind !== 'transfer') form.kind = s.kind
  if (s.category_id && categories.byId(s.category_id)) form.category_id = s.category_id
  if (s.account_id && accounts.byId(s.account_id) && !accounts.byId(s.account_id).archived_at) form.account_id = s.account_id
  suggestions.value = []
}
function hideSuggest () { setTimeout(() => { suggestions.value = [] }, 150) }

// ---- 檢查與送出 ----
function clearErrors () { Object.keys(errors).forEach(k => delete errors[k]) }

function amountOf (field, currency, label, required) {
  const raw = form[field]
  if (!String(raw ?? '').trim()) { if (required) errors[field] = `請輸入${label}`; return null }
  try {
    const v = toAmountString(raw, digitsOf(currency))
    if (Number(v) <= 0) { errors[field] = `${label}必須大於 0`; return null }
    return v
  } catch (e) { errors[field] = e.message; return null }
}

function buildBody () {
  clearErrors()
  if (!form.account_id) errors.account_id = '請選擇帳戶'
  const cur = fromAccount.value?.currency || 'TWD'
  const body = {
    kind: form.kind,
    date: form.date,
    title: form.title.trim(),
    note: form.note.trim(),
    tags: form.tags.split(/[\s,，]+/).map(s => s.replace(/^#/, '')).filter(Boolean),
    account_id: form.account_id,
    amount: amountOf('amount', cur, '金額', true)
  }
  if (!form.date) errors.date = '請選擇日期'
  else if (minDate.value && form.date < minDate.value) errors.date = `不可早於開戶日期 ${minDate.value}`
  if (form.kind === 'transfer') {
    if (!form.to_account_id) errors.to_account_id = '請選擇轉入帳戶'
    else if (form.to_account_id === form.account_id) errors.to_account_id = '轉出與轉入帳戶不能相同'
    body.to_account_id = form.to_account_id
    if (crossCurrency.value) body.to_amount = amountOf('to_amount', toAccount.value.currency, '轉入金額', true)
    body.fee = amountOf('fee', cur, '手續費', false)
  } else {
    if (!body.title) errors.title = '請輸入項目名稱'
    if (!form.category_id) errors.category_id = '請選擇分類'
    body.category_id = form.category_id
  }
  if (editing.value) body.version = editing.value.version
  return Object.keys(errors).length ? null : body
}

async function save () {
  if (saving.value) return
  const body = buildBody()
  if (!body) return
  saving.value = true
  try {
    if (editing.value) await api.put(`/transactions/${editing.value.id}`, body)
    else await api.post('/transactions', body)
    try { localStorage.setItem(LAST_ACCOUNT, body.account_id) } catch { /* 忽略 */ }
    toast.success(editing.value ? '已更新交易' : `已記帳「${body.title || '轉帳'}」`)
    ui.ledgerChanged()
    accounts.load().catch(() => {})
    if (continuous.value && !editing.value) {
      Object.assign(form, { title: '', amount: '', note: '', tags: '', to_amount: '', fee: '' })
      await nextTick()
      amountEl.value?.focus()
    } else close()
  } finally {
    saving.value = false
  }
}

async function remove () {
  await api.delete(`/transactions/${editing.value.id}`)
  toast.success('已刪除交易')
  ui.ledgerChanged()
  accounts.load().catch(() => {})
  close()
}

function copyAsNew () {
  const copy = { ...form, date: today() }
  editing.value = null
  ui.openTx({ preset: copy })
}

function close () {
  ui.closeTx()
  editing.value = null
}
</script>
