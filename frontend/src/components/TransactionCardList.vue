<template>
  <div class="flex flex-col gap-2">
    <div v-for="(r, i) in batch.rows" :key="r.key" :class="wrapClass(r)">
      <button type="button" class="flex min-h-[52px] w-full items-center gap-2.5 px-3 py-1.5 text-left text-text" :aria-expanded="open === r.key" @click="open = open === r.key ? null : r.key">
        <Badge :tone="badgeTone(r)" class="min-w-[38px] justify-center">{{ badgeText(r) }}</Badge>
        <span class="flex min-w-0 flex-1 flex-col gap-0.5">
          <span class="truncate text-sm" :class="r.deleted && 'text-muted line-through'">{{ r.title || (r.kind === 'transfer' ? '轉帳' : '（未填項目）') }}</span>
          <span class="num truncate text-[11px] text-muted">{{ meta(r) }}</span>
        </span>
        <span class="num text-sm" :class="[amountTone(r), r.deleted && 'line-through']">{{ amountText(r) }}</span>
      </button>
      <div v-if="open === r.key" class="flex flex-col gap-3 border-t border-line px-3 pt-3 pb-3.5">
        <div v-if="batch.errors[r.key]" role="alert" class="text-xs text-exp">{{ Object.values(batch.errors[r.key]).join('；') }}</div>
        <div class="grid grid-cols-3 gap-1 rounded-[10px] border border-line bg-panel2 p-1">
          <button v-for="k in kinds" :key="k.value" type="button" :aria-pressed="r.kind === k.value" :disabled="r.deleted"
            class="h-9 rounded-lg text-sm" :class="r.kind === k.value ? 'bg-panel font-semibold text-accent' : 'text-muted'" @click="batch.set(r.key, 'kind', k.value)">{{ k.label }}</button>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <label class="flex flex-col gap-1"><span class="lbl">日期</span><input type="date" :value="r.date" :disabled="r.deleted" :class="field(r, 'date')" @input="e => batch.set(r.key, 'date', e.target.value)"></label>
          <label class="flex flex-col gap-1"><span class="lbl">金額 · {{ cur(r.account_id) }}</span><input :value="r.amount" inputmode="decimal" :disabled="r.deleted" :class="[field(r, 'amount'), 'num text-right']" @input="e => batch.set(r.key, 'amount', e.target.value)"></label>
        </div>
        <label class="flex flex-col gap-1"><span class="lbl">項目</span><input :value="r.title" :disabled="r.deleted" :class="field(r, 'title')" @input="e => batch.set(r.key, 'title', e.target.value)"></label>
        <div class="grid grid-cols-2 gap-2">
          <label v-if="r.kind !== 'transfer'" class="flex flex-col gap-1"><span class="lbl">分類</span>
            <select :value="r.category_id ?? ''" :disabled="r.deleted" :class="field(r, 'category_id')" @change="e => batch.set(r.key, 'category_id', e.target.value || null)">
              <option value="">請選擇</option>
              <optgroup v-for="g in categoryGroups(r.kind)" :key="g.label" :label="g.label"><option v-for="o in g.options" :key="o.value" :value="o.value">{{ o.label }}</option></optgroup>
            </select>
          </label>
          <label class="flex flex-col gap-1"><span class="lbl">{{ r.kind === 'transfer' ? '轉出帳戶' : '帳戶' }}</span>
            <select :value="r.account_id ?? ''" :disabled="r.deleted" :class="field(r, 'account_id')" @change="e => batch.set(r.key, 'account_id', e.target.value || null)">
              <option value="">請選擇</option><option v-for="a in usable(r, 'account_id')" :key="a.id" :value="a.id">{{ a.name }} · {{ a.currency }}</option>
            </select>
          </label>
          <label v-if="r.kind === 'transfer'" class="flex flex-col gap-1"><span class="lbl">轉入帳戶</span>
            <select :value="r.to_account_id ?? ''" :disabled="r.deleted" :class="field(r, 'to_account_id')" @change="e => batch.set(r.key, 'to_account_id', e.target.value || null)">
              <option value="">請選擇</option><option v-for="a in usable(r, 'to_account_id')" :key="a.id" :value="a.id">{{ a.name }} · {{ a.currency }}</option>
            </select>
          </label>
        </div>
        <label v-if="r.kind === 'transfer' && cur(r.to_account_id) && cur(r.to_account_id) !== cur(r.account_id)" class="flex flex-col gap-1"><span class="lbl">轉入金額 · {{ cur(r.to_account_id) }}</span>
          <input :value="r.to_amount" inputmode="decimal" :disabled="r.deleted" :class="[field(r, 'to_amount'), 'num text-right']" @input="e => batch.set(r.key, 'to_amount', e.target.value)">
        </label>
        <div class="flex gap-2">
          <button type="button" class="h-10 flex-1 rounded-[9px] border border-line bg-panel2 text-[13px] text-text" @click="batch.duplicate(i)">複製這筆</button>
          <button v-if="rowStatus(r) === 'modified'" type="button" class="h-10 flex-1 rounded-[9px] border border-line bg-panel2 text-[13px] text-text" @click="batch.revert(r.key)">還原</button>
          <button type="button" class="h-10 flex-1 rounded-[9px] border border-exp text-[13px] text-exp" @click="batch.toggleDelete(r.key)">{{ r.deleted ? '取消刪除' : r.id ? '標記刪除' : '移除' }}</button>
        </div>
      </div>
    </div>
    <button type="button" class="flex h-12 items-center justify-center gap-2 rounded-xl border border-dashed border-accent text-sm text-accent" @click="add">
      <Plus :size="15" aria-hidden="true" />新增一筆（沿用上一筆的日期與帳戶）
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import Badge from './ui/Badge.vue'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { useTransactionBatchStore } from '../stores/transactionBatch'
import { rowStatus } from '../utils/batch'
import { formatAmount } from '../utils/money'

// 表格模式（手機，SA FR-3.20）：一張卡片一筆，點開編輯；規則與桌面表格相同（不支援貼上）
const batch = useTransactionBatchStore()
const accounts = useAccountsStore()
const categories = useCategoriesStore()
const open = ref(null)
const kinds = [{ value: 'expense', label: '支出' }, { value: 'income', label: '收入' }, { value: 'transfer', label: '轉帳' }]

const cur = (id) => accounts.byId(id)?.currency ?? ''
const usable = (r, f) => accounts.list.filter(a => !a.archived_at || a.id === r.original?.[f])
const categoryGroups = (kind) => categories.tree(kind).map(p => ({ label: p.name, options: [{ value: p.id, label: p.name }, ...p.children.map(c => ({ value: c.id, label: `${p.name} › ${c.name}` }))] }))
const hasErr = (r) => !!batch.errors[r.key] || batch.conflicts.includes(r.key)
const badgeTone = (r) => hasErr(r) ? 'exp' : { new: 'accent', modified: 'warn', deleted: 'muted', clean: 'muted', blank: 'muted' }[rowStatus(r)]
const badgeText = (r) => hasErr(r) ? '錯誤' : { new: '新增', modified: '修改', deleted: '刪除', clean: '未變', blank: '空白' }[rowStatus(r)]
const wrapClass = (r) => ['overflow-hidden rounded-xl border bg-panel', hasErr(r) ? 'border-exp' : open.value === r.key ? 'border-accent' : 'border-line', r.deleted && 'opacity-60']
const field = (r, f) => ['h-[42px] w-full rounded-[9px] border bg-panel2 px-2.5 text-sm text-text outline-none focus:border-accent', batch.errors[r.key]?.[f] ? 'border-exp' : 'border-line']
const meta = (r) => [r.date?.slice(5), r.kind === 'transfer' ? `${accounts.byId(r.account_id)?.name ?? '?'} → ${accounts.byId(r.to_account_id)?.name ?? '?'}` : categories.byId(r.category_id) ? categories.path(r.category_id) : '未選分類', r.kind !== 'transfer' && accounts.byId(r.account_id)?.name].filter(Boolean).join(' · ')
const amountTone = (r) => r.kind === 'income' ? 'text-inc' : r.kind === 'expense' ? 'text-exp' : 'text-muted'
const amountText = (r) => {
  if (!r.amount) return '—'
  const n = Number(r.amount)
  if (!Number.isFinite(n)) return r.amount
  return r.kind === 'transfer' ? '⇄ ' + formatAmount(n, cur(r.account_id) || 'TWD') : formatAmount(r.kind === 'income' ? n : -n, cur(r.account_id) || 'TWD', { sign: 'always' })
}

function add () {
  const row = batch.addRow()
  if (row) open.value = row.key
}
</script>
