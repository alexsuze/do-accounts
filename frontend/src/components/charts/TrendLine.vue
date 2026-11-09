<template>
  <div class="flex flex-col gap-1">
    <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="h-[200px] w-full" role="img" :aria-label="label">
      <path :d="`M0 ${y(0)}H${W}`" stroke="var(--muted)" stroke-width="1" vector-effect="non-scaling-stroke" />
      <path v-if="budgetPath" :d="budgetPath" fill="none" stroke="var(--warn)" stroke-width="2" stroke-dasharray="8 6" vector-effect="non-scaling-stroke" />
      <path v-if="points.length > 1" :d="`M0 ${y(Number(average))}H${W}`" stroke="var(--muted)" stroke-width="2" stroke-dasharray="2 5" vector-effect="non-scaling-stroke" />
      <path :d="area" fill="var(--accent-soft)" />
      <path :d="line" fill="none" stroke="var(--accent)" stroke-width="3" vector-effect="non-scaling-stroke" />
      <rect v-for="(p, i) in points" :key="p.month" :x="x(i) - 4" :y="y(Number(p.amount)) - 4" width="8" height="8" fill="var(--accent)">
        <title>{{ p.month }}：{{ formatAmount(p.amount, currency) }}{{ p.budget ? `（預算 ${formatAmount(p.budget, currency)}）` : '' }}</title>
      </rect>
    </svg>
    <div class="num grid text-center text-[11px] text-muted" :style="{ gridTemplateColumns: `repeat(${points.length}, minmax(0, 1fr))` }">
      <span v-for="(p, i) in points" :key="p.month" :class="[over(p) && 'font-semibold text-exp', points.length > 6 && (points.length - 1 - i) % 2 && 'max-sm:invisible']">{{ p.month.slice(5) }}月</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { linePath, niceMax, scale } from '../../utils/charts'
import { formatAmount } from '../../utils/money'

// 分類趨勢折線（SA FR-6.4）：每月金額、預算（虛線，各月可能不同）、平均（點線）
const props = defineProps({
  points: { type: Array, required: true },
  average: { type: [String, Number], default: 0 },
  currency: { type: String, default: 'TWD' },
  label: { type: String, required: true }
})
const W = 720
const H = 200
const top = computed(() => niceMax(Math.max(1, ...props.points.flatMap(p => [Number(p.amount), Number(p.budget ?? 0)])) * 1.1))
const y = (v) => scale(0, top.value, H - 20, 12)(v)
const step = computed(() => W / Math.max(1, props.points.length))
const x = (i) => i * step.value + step.value / 2
const line = computed(() => linePath(props.points.map((p, i) => [x(i), y(Number(p.amount))])))
const area = computed(() => props.points.length ? `${line.value}L${x(props.points.length - 1)} ${y(0)}L${x(0)} ${y(0)}Z` : '')
// 預算可能逐月不同：每個月畫一段水平線
const budgetPath = computed(() => props.points.map((p, i) => p.budget == null ? '' : `M${i * step.value} ${y(Number(p.budget)).toFixed(1)}H${(i + 1) * step.value}`).join(''))
const over = (p) => p.budget != null && Number(p.amount) > Number(p.budget)
</script>
