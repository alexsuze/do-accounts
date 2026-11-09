<template>
  <div class="flex flex-col gap-1.5">
    <div class="relative mt-4 flex items-end gap-[3px] border-b border-muted pt-5" :style="{ height: `${height}px` }" role="img" :aria-label="label">
      <div aria-hidden="true" class="absolute inset-x-0 border-t border-dashed border-line" :style="{ bottom: `${refY}px` }" />
      <div v-for="d in days" :key="d.date" class="relative flex h-full min-w-0 flex-1 items-end justify-center gap-px" :title="tip(d)">
        <span v-if="Number(d.income) > cap" class="num absolute -top-4 text-[10px] whitespace-nowrap text-inc">▲{{ compact(d.income) }}</span>
        <span v-if="Number(d.expense) > cap" class="num absolute text-[10px] whitespace-nowrap text-exp" :class="Number(d.income) > cap ? '-top-8' : '-top-4'">▲{{ compact(d.expense) }}</span>
        <div class="w-[40%] rounded-t-sm bg-inc" :style="{ height: `${h(d.income)}px` }" />
        <div class="w-[45%] rounded-t-sm bg-exp" :style="{ height: `${h(d.expense)}px` }" />
      </div>
    </div>
    <div class="num flex justify-between text-[11px] text-muted">
      <span v-for="t in ticks" :key="t">{{ t }}</span>
    </div>
    <span class="text-xs text-muted">虛線為 {{ formatAmount(ref_, currency) }}。<template v-if="hasBig">超出圖表高度的金額以 ▲ 標示。</template></span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { compact, dailyCap } from '../../utils/charts'
import { formatAmount } from '../../utils/money'
import { shortDate } from '../../utils/date'

// 每日收支長條圖（SA FR-6.3）。高度以一般的支出為準，特別大的金額（薪資、房租）截斷並標示金額
const props = defineProps({
  days: { type: Array, required: true },
  currency: { type: String, default: 'TWD' },
  label: { type: String, required: true },
  height: { type: Number, default: 170 }
})
const inner = computed(() => props.height - 20)
const cap = computed(() => dailyCap(props.days.map(d => Number(d.expense))))
const h = (v) => Math.min(Number(v) / cap.value, 1) * inner.value
const ref_ = computed(() => cap.value / 2)
const refY = computed(() => inner.value / 2)
const hasBig = computed(() => props.days.some(d => Number(d.income) > cap.value || Number(d.expense) > cap.value))
const tip = (d) => `${shortDate(d.date)} 支出 ${formatAmount(d.expense, props.currency)}${Number(d.income) ? ` · 收入 ${formatAmount(d.income, props.currency)}` : ''}`
const ticks = computed(() => {
  const n = props.days.length
  if (!n) return []
  return [0, 7, 14, 21, n - 1].filter((i, k, a) => i < n && a.indexOf(i) === k).map(i => shortDate(props.days[i].date))
})
</script>
