<template>
  <div class="flex min-h-14 flex-wrap items-center gap-3 border-b border-line px-4 py-2.5 last:border-b-0 sm:flex-nowrap sm:px-5" :class="selected && 'bg-accent-soft'">
    <label v-if="selectable" class="flex min-h-11 items-center" @click.stop>
      <input type="checkbox" :checked="selected" :aria-label="`選取 ${tx.title}`" class="size-[18px] accent-[var(--accent)]" @change="emit('toggle')">
    </label>
    <span v-if="tx.kind === 'transfer'" class="flex size-8 shrink-0 items-center justify-center rounded-[9px] border border-line text-muted"><ArrowLeftRight :size="16" aria-hidden="true" /></span>
    <span v-else-if="tx.kind === 'investment'" class="flex size-8 shrink-0 items-center justify-center rounded-[9px] border border-accent text-accent"><TrendingUp :size="16" aria-hidden="true" /></span>
    <CategoryIcon v-else :icon="root?.icon" :color="root?.color" />
    <button type="button" class="flex min-w-0 flex-1 flex-col gap-0.5 text-left text-text" @click="emit('open')">
      <span class="truncate font-medium">{{ tx.title }}</span>
      <span class="truncate text-xs text-muted">{{ meta }}</span>
    </button>
    <span v-if="showDate" class="num hidden text-xs text-muted sm:inline">{{ shortDate(tx.date) }}</span>
    <span class="flex flex-col items-end">
      <Amount :value="amount" :currency="currency" :kind="tx.kind === 'investment' ? '' : tx.kind" class="text-[15px] font-medium" />
      <span v-if="toText" class="num text-[11px] text-muted">{{ toText }}</span>
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowLeftRight, TrendingUp } from 'lucide-vue-next'
import Amount from './ui/Amount.vue'
import CategoryIcon from './CategoryIcon.vue'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { formatAmount } from '../utils/money'
import { shortDate } from '../utils/date'

// 一筆交易（明細、總覽最近交易）
const props = defineProps({ tx: { type: Object, required: true }, selectable: Boolean, selected: Boolean, showDate: Boolean })
const emit = defineEmits(['open', 'toggle'])
const accounts = useAccountsStore()
const categories = useCategoriesStore()

const root = computed(() => categories.root(props.tx.category_id))
const entry = (role) => props.tx.entries.find(e => e.role === role)
const main = computed(() => entry('main') ?? entry('transfer_out') ?? props.tx.entries[0])
// 投資交易：股票股利、分割沒有現金

const acct = (id) => accounts.byId(id)
const currency = computed(() => acct(main.value?.account_id)?.currency ?? 'TWD')
const amount = computed(() => main.value?.amount)
const meta = computed(() => {
  const t = props.tx
  if (t.kind === 'transfer') {
    const fee = entry('fee')
    const parts = [`${acct(entry('transfer_out')?.account_id)?.name ?? '?'} → ${acct(entry('transfer_in')?.account_id)?.name ?? '?'}`]
    if (fee) parts.push(`手續費 ${formatAmount(-fee.amount, currency.value)}`)
    if (t.tags?.length) parts.push(t.tags.map(x => '#' + x).join(' '))
    return parts.join(' · ')
  }
  if (t.kind === 'investment') {
    const holder = acct(t.investment?.account_id)?.name
    const settle = main.value && main.value.account_id !== t.investment?.account_id ? `交割：${acct(main.value.account_id)?.name}` : ''
    return ['投資', holder, settle].filter(Boolean).join(' · ')
  }
  return [categories.path(t.category_id), acct(main.value?.account_id)?.name, ...(t.tags ?? []).map(x => '#' + x)].filter(Boolean).join(' · ')
})
// 跨幣別轉帳另外顯示轉入金額
const toText = computed(() => {
  if (props.tx.kind !== 'transfer') return ''
  const tin = entry('transfer_in')
  const toCur = acct(tin?.account_id)?.currency
  return toCur && toCur !== currency.value ? `→ ${formatAmount(tin.amount, toCur, { symbol: true })}` : ''
})
</script>
