<template>
  <div class="relative size-[168px] shrink-0">
    <svg width="168" height="168" viewBox="0 0 168 168" role="img" :aria-label="`總預算已使用 ${pctText}`">
      <circle cx="84" cy="84" r="70" fill="none" stroke="var(--panel2)" stroke-width="14" />
      <circle cx="84" cy="84" r="70" fill="none" :stroke="`var(--${tone === 'inc' ? 'accent' : tone})`" stroke-width="14" stroke-linecap="round"
        :stroke-dasharray="`${len.toFixed(1)} ${C.toFixed(1)}`" transform="rotate(-90 84 84)" />
      <circle cx="84" cy="84" r="56" fill="none" stroke="var(--line)" stroke-width="1" stroke-dasharray="2 6" />
    </svg>
    <div class="absolute inset-0 flex flex-col items-center justify-center gap-0.5">
      <span class="num text-[34px] font-semibold" :class="{ 'text-warn': tone === 'warn', 'text-exp': tone === 'exp' }">{{ pctText }}</span>
      <span class="lbl">已使用</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

// 總預算圓環（預算頁）
const props = defineProps({ percentage: { type: [String, Number], required: true }, tone: { type: String, default: 'inc' } })
const C = 2 * Math.PI * 70
const len = computed(() => Math.min(1, Math.max(0, Number(props.percentage) / 100)) * C)
const pctText = computed(() => `${Math.round(Number(props.percentage))}%`)
</script>
