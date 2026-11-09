<template>
  <div class="flex flex-col gap-4">
    <header class="flex flex-wrap items-center gap-3">
      <IconButton label="返回明細" @click="router.push({ path: '/transactions', query: backQuery })"><ChevronLeft :size="18" aria-hidden="true" /></IconButton>
      <div class="mr-auto flex flex-col gap-1">
        <span class="lbl">Batch edit · 表格模式</span>
        <h1 class="m-0 font-head text-[26px] leading-tight font-bold">多筆編輯</h1>
      </div>
      <span class="num hidden rounded-lg border border-dashed border-line px-3 py-2 text-xs text-muted md:inline">
        {{ batch.sourceLabel }}<template v-if="batch.draftSavedAt"> · 草稿已暫存 {{ timeOf(batch.draftSavedAt) }}</template>
      </span>
      <ThemeToggle />
      <AppButton variant="ghost" @click="router.push({ path: '/transactions', query: backQuery })">取消</AppButton>
      <AppButton :loading="batch.saving" :disabled="!batch.changeCount" @click="save">
        <Check :size="16" aria-hidden="true" />儲存全部 <span class="num text-xs opacity-80">（{{ batch.changeCount }} 項變更）</span>
      </AppButton>
    </header>

    <div v-if="restorable" role="status" class="flex flex-wrap items-center gap-3 rounded-xl border border-accent bg-accent-soft px-4 py-3 text-sm">
      <History :size="18" class="text-accent" aria-hidden="true" />
      <span class="mr-auto">有上次沒儲存的草稿（{{ restorable.label }} · {{ timeOf(restorable.savedAt) }}），要還原嗎？</span>
      <AppButton size="sm" @click="restore">還原草稿</AppButton>
      <AppButton size="sm" variant="ghost" @click="discardDraft">捨棄</AppButton>
    </div>

    <div v-if="batch.errorRows || batch.conflicts.length" role="alert" class="flex flex-wrap items-center gap-3 rounded-xl border border-exp bg-exp-soft px-4 py-3">
      <CircleAlert :size="18" class="text-exp" aria-hidden="true" />
      <span class="font-semibold text-exp">{{ batch.conflicts.length ? `有 ${batch.conflicts.length} 筆紀錄已在其他地方被修改或刪除` : `有 ${batch.errorRows} 列資料有誤` }}，未儲存任何資料</span>
      <span class="num text-[13px]">{{ firstError }}</span>
      <AppButton v-if="batch.conflicts.length" size="sm" variant="ghost" title="載入最新的資料，這次的修改會捨棄" @click="reload({ discardDraft: true })">重新載入（捨棄這次修改）</AppButton>
    </div>

    <p v-if="batch.truncated" class="m-0 rounded-xl border border-warn bg-warn-soft px-4 py-3 text-sm">
      篩選結果超過 {{ MAX_ROWS }} 筆，只載入最新的 {{ MAX_ROWS }} 筆（另有 {{ batch.truncated }} 筆未載入）。請縮小日期範圍後再編輯其他筆。
    </p>

    <div v-if="batch.loading" class="rounded-[14px] border border-line bg-panel py-12 text-center text-muted">載入中…</div>
    <TransactionGrid v-else-if="desktop" :ctx="ctx" @pasted="n => toast.info(`已貼上 ${n} 列，請檢查標紅的格子`)" />
    <TransactionCardList v-else />

    <section class="flex flex-wrap gap-4">
      <AppCard class="flex min-w-0 flex-[1_1_320px] flex-wrap items-center gap-5">
        <span class="lbl w-full">本次變更</span>
        <span class="flex items-center gap-2"><span class="size-2 rounded-full bg-accent" aria-hidden="true" />新增 <b class="num">{{ batch.summary.new }}</b></span>
        <span class="flex items-center gap-2"><span class="size-2 rounded-full bg-warn" aria-hidden="true" />修改 <b class="num">{{ batch.summary.modified }}</b></span>
        <span class="flex items-center gap-2"><span class="size-2 rounded-[1px] bg-muted" aria-hidden="true" />刪除 <b class="num">{{ batch.summary.deleted }}</b></span>
        <span class="flex items-center gap-2 text-exp"><span class="size-2 rounded-full border-2 border-exp" aria-hidden="true" />錯誤 <b class="num">{{ batch.errorRows + batch.conflicts.length }}</b></span>
      </AppCard>
      <AppCard class="flex min-w-0 flex-[1_1_300px] flex-col gap-2">
        <span class="lbl">本次合計（新增＋修改後）</span>
        <div class="num flex flex-wrap gap-x-6 gap-y-1 text-[17px]">
          <template v-for="(t, c) in totals.byCurrency" :key="c">
            <span class="text-inc">收入 {{ formatAmount(t.income, c, { sign: 'always' }) }}</span>
            <span class="text-exp">支出 {{ formatAmount(-t.expense, c, { sign: 'always' }) }}<span v-if="Object.keys(totals.byCurrency).length > 1" class="text-xs text-muted"> {{ c }}</span></span>
          </template>
          <span v-if="!Object.keys(totals.byCurrency).length" class="text-muted">—</span>
          <span v-if="totals.transfers" class="text-muted">轉帳 {{ totals.transfers }} 筆</span>
        </div>
      </AppCard>
      <AppCard v-if="desktop" class="flex min-w-0 flex-[1_1_360px] flex-col gap-2.5">
        <span class="lbl">快速鍵</span>
        <div class="flex flex-wrap gap-3.5 text-[13px] text-muted">
          <span><kbd>Tab</kbd> 下一格</span><span><kbd>Enter</kbd> 下一列</span><span><kbd>Ctrl</kbd>+<kbd>V</kbd> 從 Excel 貼上</span>
          <span><kbd>Ctrl</kbd>+<kbd>D</kbd> 往下填滿</span><span><kbd>Ctrl</kbd>+<kbd>S</kbd> 儲存全部</span>
        </div>
      </AppCard>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Check, ChevronLeft, CircleAlert, History } from 'lucide-vue-next'
import TransactionCardList from '../components/TransactionCardList.vue'
import TransactionGrid from '../components/TransactionGrid.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import IconButton from '../components/ui/IconButton.vue'
import ThemeToggle from '../components/ui/ThemeToggle.vue'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { useTransactionBatchStore } from '../stores/transactionBatch'
import { useUiStore } from '../stores/ui'
import { toast } from '../stores/toast'
import { useMediaQuery } from '../composables/useMediaQuery'
import { MAX_ROWS, batchTotals, rowStatus } from '../utils/batch'
import { monthRange, thisMonth } from '../utils/date'
import { formatAmount } from '../utils/money'

// 多筆編輯（SA FR-3.10～3.20、UC-02b）。網址帶明細的篩選條件時載入既有交易，否則為空白表格
const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const accounts = useAccountsStore()
const categories = useCategoriesStore()
const batch = useTransactionBatchStore()
const desktop = useMediaQuery('(min-width: 1024px)')
const restorable = ref(null)

const ctx = computed(() => ({
  accountsById: Object.fromEntries(accounts.list.map(a => [a.id, a])),
  categoriesById: Object.fromEntries(categories.list.map(c => [c.id, c]))
}))
const totals = computed(() => batchTotals(batch.rows, ctx.value))
const backQuery = computed(() => { const q = { ...route.query }; delete q.blank; return q })
const firstError = computed(() => {
  const i = batch.rows.findIndex(r => batch.errors[r.key] || batch.conflicts.includes(r.key))
  if (i < 0) return ''
  const r = batch.rows[i]
  return `第 ${i + 1} 列 · ${batch.conflicts.includes(r.key) ? '已被其他地方修改' : Object.values(batch.errors[r.key])[0]}`
})
const timeOf = (iso) => iso ? new Date(iso).toLocaleTimeString('zh-TW', { hour12: false }) : ''

/** 由網址組出要載入的明細查詢與說明文字 */
function sourceOf (q) {
  if (q.blank || !Object.keys(q).length) return { query: '', label: '空白表格' }
  const month = /^\d{4}-\d{2}$/.test(q.month) ? q.month : thisMonth()
  const { from, to } = monthRange(month)
  const p = new URLSearchParams({ from, to })
  if (q.kind) p.set('kind', q.kind)
  if (q.account) p.set('account_id', q.account)
  if (q.category) p.set('category_id', q.category)
  if (q.q) p.set('q', q.q)
  const parts = [`${month} 明細`]
  if (q.kind) parts.push({ expense: '支出', income: '收入', transfer: '轉帳' }[q.kind])
  if (q.account) parts.push(accounts.byId(q.account)?.name)
  if (q.category) parts.push(categories.path(q.category))
  if (q.q) parts.push(`「${q.q}」`)
  return { query: p.toString(), label: parts.filter(Boolean).join(' · ') }
}

/** discardDraft：版本衝突後重新載入時要丟掉草稿，否則還原會帶回過期的版本號 */
async function reload ({ discardDraft = false } = {}) {
  clearTimeout(timer)
  if (discardDraft) batch.clearDraft()
  await Promise.all([accounts.ensure(), categories.ensure()])
  const s = sourceOf(route.query)
  // 載入前先讀草稿並標記，避免載入觸發的暫存把草稿蓋掉
  const d = batch.readDraft()
  restorable.value = d && d.source === s.query && d.rows?.some(r => rowStatus(r) !== 'clean' && rowStatus(r) !== 'blank') ? d : null
  await batch.load(s.query, s.label)
}

function restore () {
  batch.restoreDraft(restorable.value)
  restorable.value = null
  toast.info('已還原草稿')
}
function discardDraft () {
  batch.clearDraft()
  restorable.value = null
  batch.saveDraft()
}

async function save () {
  if (await batch.save(ctx.value)) {
    ui.ledgerChanged()
    accounts.load().catch(() => {})
    await reload({ discardDraft: true })
  }
}

// 草稿：變更後 1 秒暫存（SA FR-3.18）
let timer
watch(() => batch.rows, () => {
  if (restorable.value) return
  clearTimeout(timer)
  timer = setTimeout(() => { if (!restorable.value) batch.saveDraft() }, 1000)
}, { deep: true })

// 離開提醒
const dirty = () => batch.changeCount > 0
onBeforeRouteLeave(() => !dirty() || window.confirm('還有尚未儲存的變更，確定要離開嗎？（草稿已暫存，下次開啟可以還原）'))
const beforeUnload = (e) => { if (dirty()) { e.preventDefault(); e.returnValue = '' } }
// Ctrl+S 儲存全部（只在這一頁）
const onKey = (e) => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') { e.preventDefault(); save() } }

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  window.addEventListener('keydown', onKey)
  reload()
})
onBeforeUnmount(() => {
  clearTimeout(timer)
  window.removeEventListener('beforeunload', beforeUnload)
  window.removeEventListener('keydown', onKey)
})
watch(() => route.query, (q, old) => { if (JSON.stringify(q) !== JSON.stringify(old)) reload() })
</script>

<style scoped>
kbd { font-family: var(--font-mono); font-size: 11px; padding: 2px 6px; border: 1px solid var(--line); border-bottom-width: 2px; border-radius: 5px; color: var(--text); background: var(--panel2); }
</style>
