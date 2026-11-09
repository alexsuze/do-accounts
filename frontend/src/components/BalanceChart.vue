<template>
  <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="h-[180px] w-full" role="img" :aria-label="label">
    <path :d="`M0 ${H * 0.25}H${W}M0 ${H * 0.5}H${W}M0 ${H * 0.75}H${W}`" stroke="var(--line)" stroke-width="1" vector-effect="non-scaling-stroke" />
    <path v-if="zeroY !== null" :d="`M0 ${zeroY}H${W}`" stroke="var(--muted)" stroke-dasharray="4 4" stroke-width="1" vector-effect="non-scaling-stroke" />
    <path :d="area" fill="var(--accent-soft)" />
    <path :d="line" fill="none" stroke="var(--accent)" stroke-width="2.5" vector-effect="non-scaling-stroke" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

// 每日餘額走勢（帳戶詳細頁）。階段 2 報表改用 Chart.js；這裡只需要一條線，用 SVG 即可
const props = defineProps({ points: { type: Array, required: true }, label: { type: String, default: '每日餘額走勢' } })
const W = 900
const H = 180
const values = computed(() => props.points.map(p => Number(p.balance)))
const range = computed(() => {
  const min = Math.min(...values.value); const max = Math.max(...values.value)
  const pad = (max - min) * 0.1 || Math.abs(max) * 0.1 || 1
  return { min: min - pad, max: max + pad }
})
const y = (v) => H - (v - range.value.min) / (range.value.max - range.value.min) * H
const x = (i) => values.value.length < 2 ? W / 2 : i / (values.value.length - 1) * W
const line = computed(() => values.value.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(v).toFixed(1)}`).join(''))
const area = computed(() => values.value.length ? `${line.value}L${x(values.value.length - 1)} ${H}L0 ${H}Z` : '')
const zeroY = computed(() => range.value.min < 0 && range.value.max > 0 ? y(0) : null)
</script>
