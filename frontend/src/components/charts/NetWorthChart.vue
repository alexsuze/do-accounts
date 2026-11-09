<template>
  <div class="flex flex-col gap-1">
    <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="w-full" :style="{ height: `${height}px` }" role="img" :aria-label="aria">
      <path :d="grid" stroke="var(--line)" stroke-width="1" vector-effect="non-scaling-stroke" />
      <path :d="`M0 ${y(0)}H${W}`" stroke="var(--muted)" stroke-width="1" vector-effect="non-scaling-stroke" />
      <g v-for="(p, i) in points" :key="p.month">
        <title>{{ tip(p) }}</title>
        <rect :x="barX(i)" :y="y(Number(p.assets))" :width="barW" :height="Math.max(0, y(0) - y(Number(p.assets)))" fill="var(--c2)" opacity=".75" />
        <rect :x="barX(i)" :y="y(0)" :width="barW" :height="Math.max(0, y(Number(p.liabilities)) - y(0))" fill="var(--exp)" opacity=".75" />
      </g>
      <path :d="line" fill="none" stroke="var(--accent)" stroke-width="3" vector-effect="non-scaling-stroke" />
    </svg>
    <div class="num grid text-center text-[11px] text-muted" :style="{ gridTemplateColumns: `repeat(${points.length}, minmax(0, 1fr))` }">
      <span v-for="(p, i) in points" :key="p.month" :class="!showLabel(i) ? 'invisible' : narrowHide(i) ? 'max-sm:invisible' : ''">{{ monthLabel(p.month, i) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { linePath, niceMax, scale } from '../../utils/charts'
import { formatAmount } from '../../utils/money'

// 淨資產趨勢（SA FR-6.1）：資產（向上的長條）、負債（向下的長條）、淨資產（折線）
const props = defineProps({
  points: { type: Array, required: true },
  currency: { type: String, default: 'TWD' },
  height: { type: Number, default: 250 }
})
const W = 720
const H = 250
const PAD = 12
const top = computed(() => niceMax(Math.max(0, ...props.points.map(p => Math.max(Number(p.assets), Number(p.net_worth))))))
const bottom = computed(() => {
  const min = Math.min(0, ...props.points.map(p => Math.min(Number(p.liabilities), Number(p.net_worth))))
  return min < 0 ? -niceMax(-min) : 0
})
const y = (v) => scale(bottom.value, top.value, H - PAD, PAD)(v)
const step = computed(() => W / Math.max(1, props.points.length))
const barW = computed(() => Math.min(40, step.value * 0.45))
const barX = (i) => i * step.value + (step.value - barW.value) / 2
const line = computed(() => linePath(props.points.map((p, i) => [i * step.value + step.value / 2, y(Number(p.net_worth))])))
const grid = computed(() => [0.25, 0.5, 0.75].map(k => `M0 ${PAD + (H - 2 * PAD) * k}H${W}`).join(''))
const fmt = (v) => formatAmount(v, props.currency)
const tip = (p) => `${p.month}　資產 ${fmt(p.assets)}　負債 ${fmt(p.liabilities)}　淨資產 ${fmt(p.net_worth)}`
// 月份多時只顯示部分標籤，避免擠在一起
const every = computed(() => Math.ceil(props.points.length / 12))
const showLabel = (i) => (props.points.length - 1 - i) % every.value === 0
// 手機上再隔一個顯示
const narrowHide = (i) => props.points.length > 6 && (props.points.length - 1 - i) % (every.value * 2) !== 0
const monthLabel = (m, i) => (i === 0 || m.endsWith('-01')) && props.points.length > 12 ? m.slice(2).replace('-', '/') : `${m.slice(5)}月`
const aria = computed(() => {
  const ps = props.points
  if (!ps.length) return '淨資產趨勢：沒有資料'
  return `${ps[0].month} 到 ${ps.at(-1).month} 的資產、負債與淨資產；淨資產由 ${fmt(ps[0].net_worth)} 變為 ${fmt(ps.at(-1).net_worth)}`
})
</script>
