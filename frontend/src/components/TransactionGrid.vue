<template>
  <div class="overflow-x-auto rounded-[14px] border border-line bg-panel">
    <table class="w-full min-w-[1370px] table-fixed border-collapse text-sm" @keydown="onKeydown" @paste="onPaste" @focusin="onFocus">
      <thead>
        <tr>
          <th class="lbl sticky top-0 w-[52px] border-b border-line bg-panel2 px-2 py-2.5 text-left font-medium">#</th>
          <th v-for="c in COLUMNS" :key="c.field" scope="col" :class="['lbl sticky top-0 border-b border-line bg-panel2 px-2 py-2.5 text-left font-medium', widths[c.field], (c.field === 'amount' || c.field === 'to_amount') && 'text-right']">{{ c.label }}</th>
          <th class="lbl sticky top-0 w-[112px] border-b border-line bg-panel2 px-2 py-2.5 text-left font-medium">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(r, i) in batch.rows" :key="r.key" :class="rowClass(r)">
          <td class="border-b border-r border-line px-2" :class="statusBar(r)">
            <span class="num flex items-center gap-1.5 text-xs" :title="statusLabel(r)">
              <span :class="dotClass(r)" aria-hidden="true" /><span>{{ i + 1 }}</span><span class="sr-only">{{ statusLabel(r) }}</span>
            </span>
          </td>
          <td v-for="(c, ci) in COLUMNS" :key="c.field" :class="cellClass(r, c.field)" :title="errOf(r, c.field) || undefined">
            <select v-if="c.field === 'kind'" :value="r.kind" v-bind="cellAttrs(r, i, ci)" :disabled="r.deleted" :class="[inputCls, kindTone[r.kind]]" @change="e => set(r, 'kind', e.target.value)">
              <option value="expense">支出</option><option value="income">收入</option><option value="transfer">轉帳</option>
            </select>
            <select v-else-if="c.field === 'category_id'" :value="r.category_id ?? ''" v-bind="cellAttrs(r, i, ci)" :disabled="r.kind === 'transfer' || r.deleted" :class="inputCls" @change="e => set(r, 'category_id', e.target.value || null)">
              <option value="">{{ r.kind === 'transfer' ? '—' : '請選擇' }}</option>
              <optgroup v-for="g in categoryGroups(r.kind)" :key="g.label" :label="g.label">
                <option v-for="o in g.options" :key="o.value" :value="o.value">{{ o.label }}</option>
              </optgroup>
            </select>
            <select v-else-if="c.field === 'account_id' || c.field === 'to_account_id'" :value="r[c.field] ?? ''" v-bind="cellAttrs(r, i, ci)"
              :disabled="(c.field === 'to_account_id' && r.kind !== 'transfer') || r.deleted" :class="inputCls" @change="e => set(r, c.field, e.target.value || null)">
              <option value="">{{ c.field === 'to_account_id' && r.kind !== 'transfer' ? '—' : '請選擇' }}</option>
              <optgroup v-for="g in accountGroups(r, c.field)" :key="g.label" :label="g.label">
                <option v-for="o in g.options" :key="o.value" :value="o.value">{{ o.label }}</option>
              </optgroup>
            </select>
            <input v-else :value="r[c.field]" v-bind="cellAttrs(r, i, ci)" :disabled="disabled(r, c.field)" :placeholder="placeholder(r, c.field)"
              :inputmode="c.field.includes('amount') ? 'decimal' : undefined"
              :class="[inputCls, (c.field === 'date' || c.field.includes('amount')) && 'num', c.field.includes('amount') && 'text-right', c.field === 'amount' && amountTone(r)]"
              @input="e => set(r, c.field, e.target.value)" @blur="normalize(r, c.field)">
          </td>
          <td class="border-b border-line px-1.5">
            <span class="flex gap-0.5">
              <button type="button" :aria-label="`複製第 ${i + 1} 列`" title="複製這一列" :class="iconBtn" @click="batch.duplicate(i)"><Copy :size="15" aria-hidden="true" /></button>
              <button v-if="statusOf(r) === 'modified'" type="button" :aria-label="`還原第 ${i + 1} 列`" title="還原成原本的值" :class="iconBtn" @click="batch.revert(r.key)"><Undo2 :size="15" aria-hidden="true" /></button>
              <button type="button" :aria-label="r.deleted ? `取消刪除第 ${i + 1} 列` : `刪除第 ${i + 1} 列`" :title="r.deleted ? '取消刪除' : r.id ? '標記刪除（儲存時才刪除）' : '移除這一列'"
                :class="iconBtn" @click="batch.toggleDelete(r.key)">
                <Undo2 v-if="r.deleted" :size="15" aria-hidden="true" /><Trash2 v-else :size="15" aria-hidden="true" />
              </button>
            </span>
          </td>
        </tr>
        <tr>
          <td :colspan="COLUMNS.length + 2">
            <button type="button" class="flex h-11 w-full items-center gap-2 px-3.5 text-accent hover:bg-panel2" @click="addAndFocus">
              <Plus :size="15" aria-hidden="true" />新增一列<span class="text-[13px] text-muted">（在最後一列按 Enter 也會新增）</span>
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { Copy, Plus, Trash2, Undo2 } from 'lucide-vue-next'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { useTransactionBatchStore } from '../stores/transactionBatch'
import { COLUMNS, changedFields, rowStatus } from '../utils/batch'
import { parseClipboard } from '../utils/paste'
import { digitsOf } from '../utils/money'
import { isExpression, toAmountString } from '../utils/expr'
import { ACCOUNT_KINDS } from '../utils/labels'

// 表格模式（桌面）：每格一個輸入框；Enter 下一列、Ctrl+D 往下填滿、可從 Excel 貼上（SA FR-3.13、3.14）
// 格子內用原生 select：鍵盤操作直覺、行動裝置有系統選單（SD 4.2.1 決策紀錄）
const props = defineProps({ ctx: { type: Object, required: true } })
const batch = useTransactionBatchStore()
const accounts = useAccountsStore()
const categories = useCategoriesStore()
const active = ref(null)

// 固定欄寬（table-fixed），項目與備註分配剩下的寬度
const widths = { date: 'w-[128px]', kind: 'w-[76px]', title: 'w-[180px]', category_id: 'w-[140px]', account_id: 'w-[140px]', amount: 'w-[100px]', to_account_id: 'w-[130px]', to_amount: 'w-[100px]', tags: 'w-[100px]', note: 'w-[120px]' }
const inputCls = 'block h-11 w-full min-w-0 border-0 bg-transparent px-2.5 text-sm text-text outline-none focus:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-40'
const iconBtn = 'flex size-9 items-center justify-center rounded-lg text-muted hover:bg-panel2 hover:text-text'
const kindTone = { expense: 'text-exp', income: 'text-inc', transfer: 'text-accent' }

const statusOf = (r) => rowStatus(r)
const errOf = (r, f) => batch.errors[r.key]?.[f] || (f === 'title' && batch.errors[r.key]?.row) || ''
const statusLabel = (r) => ({ new: '新增', modified: '已修改', deleted: '待刪除', clean: '未變更', blank: '空白' })[statusOf(r)] + (batch.errors[r.key] ? '・有錯誤' : '') + (batch.conflicts.includes(r.key) ? '・已被其他地方修改' : '')
const dotClass = (r) => {
  if (batch.errors[r.key] || batch.conflicts.includes(r.key)) return 'size-2 rounded-full border-2 border-exp'
  return { new: 'size-2 rounded-full bg-accent', modified: 'size-2 rounded-full bg-warn', deleted: 'size-2 rounded-[1px] bg-muted', clean: 'size-2 rounded-full bg-line', blank: 'size-2 rounded-full border border-line' }[statusOf(r)]
}
const statusBar = (r) => (batch.errors[r.key] || batch.conflicts.includes(r.key)) ? 'shadow-[inset_3px_0_0_var(--exp)]' : ''
const rowClass = (r) => [r.deleted && 'opacity-55 [&_input]:line-through', batch.conflicts.includes(r.key) && 'bg-exp-soft']
function cellClass (r, f) {
  const changed = r.original && changedFields(r).has(f)
  const isActive = active.value?.key === r.key && active.value?.field === f
  return ['border-b border-r border-line p-0',
    errOf(r, f) ? 'bg-exp-soft shadow-[inset_0_0_0_2px_var(--exp)]' : isActive ? 'shadow-[inset_0_0_0_2px_var(--accent)]' : changed ? 'bg-warn-soft shadow-[inset_3px_0_0_var(--warn)]' : '',
    disabled(r, f) && 'bg-panel2']
}
const cellAttrs = (r, i, ci) => ({ 'data-row': i, 'data-col': ci, 'aria-label': `第 ${i + 1} 列 ${COLUMNS[ci].label}`, 'aria-invalid': !!errOf(r, COLUMNS[ci].field) || undefined, readonly: r.deleted || undefined })

const fromCur = (r) => props.ctx.accountsById[r.account_id]?.currency
const toCur = (r) => props.ctx.accountsById[r.to_account_id]?.currency
const disabled = (r, f) => r.deleted || (f === 'to_amount' && (r.kind !== 'transfer' || !toCur(r) || toCur(r) === fromCur(r)))
const placeholder = (r, f) => ({ date: 'YYYY-MM-DD', title: r.kind === 'transfer' ? '轉帳（選填）' : '', amount: fromCur(r) ?? '', to_amount: disabled(r, 'to_amount') ? '—' : toCur(r), })[f] ?? ''
const amountTone = (r) => r.kind === 'income' ? 'text-inc' : r.kind === 'expense' ? 'text-exp' : ''

const categoryGroups = (kind) => kind === 'transfer' ? [] : categories.tree(kind).map(p => ({ label: p.name, options: [{ value: p.id, label: p.name }, ...p.children.map(c => ({ value: c.id, label: `${p.name} › ${c.name}` }))] }))
function accountGroups (r, field) {
  const keep = r.original?.[field]
  return ACCOUNT_KINDS.map(k => ({ label: k.label, options: accounts.list.filter(a => a.kind === k.value && (!a.archived_at || a.id === keep)).map(a => ({ value: a.id, label: `${a.name} · ${a.currency}` })) })).filter(g => g.options.length)
}

function set (r, field, value) { batch.set(r.key, field, value) }
/** 失焦時把算式換成結果（120+35 → 155） */
function normalize (r, field) {
  if (!field.includes('amount') || !isExpression(r[field])) return
  const cur = field === 'amount' ? fromCur(r) : toCur(r)
  try { set(r, field, toAmountString(r[field], digitsOf(cur ?? 'TWD'))) } catch { /* 錯誤留給檢查顯示 */ }
}

function onFocus (e) {
  const el = e.target.closest('[data-row]')
  if (!el) return
  const r = batch.rows[Number(el.dataset.row)]
  active.value = r ? { key: r.key, field: COLUMNS[Number(el.dataset.col)].field } : null
}

async function focusCell (row, col) {
  await nextTick()
  document.querySelector(`[data-row="${row}"][data-col="${col}"]`)?.focus()
}

async function addAndFocus () {
  batch.addRow()
  focusCell(batch.rows.length - 1, 0)
}

function onKeydown (e) {
  const el = e.target.closest('[data-row]')
  if (!el) return
  const row = Number(el.dataset.row)
  const col = Number(el.dataset.col)
  if (e.key === 'Enter' && !e.isComposing && el.tagName === 'INPUT') {
    e.preventDefault()
    normalize(batch.rows[row], COLUMNS[col].field)
    if (row === batch.rows.length - 1) batch.addRow()
    focusCell(row + 1, col)
  } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'd') {
    e.preventDefault()
    batch.fillDown(row, COLUMNS[col].field)
  }
}

/** 貼上多格（含 Tab 或換行）時接手，從目前的格子開始填（SA FR-3.14）；單一值交給瀏覽器 */
function onPaste (e) {
  const el = e.target.closest('[data-row]')
  const text = e.clipboardData?.getData('text/plain') ?? ''
  if (!el || !/[\t\n]/.test(text.trim())) return
  e.preventDefault()
  const matrix = parseClipboard(text)
  const n = batch.paste(Number(el.dataset.row), COLUMNS[Number(el.dataset.col)].field, matrix, pasteCtx.value)
  emit('pasted', n)
}
const emit = defineEmits(['pasted'])

const pasteCtx = computed(() => ({
  columns: COLUMNS,
  categoriesById: props.ctx.categoriesById,
  categoryItems: categories.list.map(c => ({ id: c.id, names: [categories.path(c.id), c.name] })),
  accountItems: accounts.list.filter(a => !a.archived_at).map(a => ({ id: a.id, names: [a.name] }))
}))
defineExpose({ pasteCtx })
</script>
