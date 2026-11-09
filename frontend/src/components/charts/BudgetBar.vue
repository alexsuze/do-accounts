<template>
  <div class="relative rounded-full bg-panel2" :class="thick ? 'h-3' : 'h-2'">
    <div class="absolute inset-y-0 left-0 rounded-full" :class="fill" :style="{ width: `${spentW}%` }" />
    <div v-if="projW > 0" class="absolute inset-y-0 opacity-70" :style="{ left: `${spentW}%`, width: `${projW}%`, background: 'repeating-linear-gradient(90deg, var(--warn) 0 4px, transparent 4px 8px)' }" />
    <div v-if="timePct != null" class="absolute -inset-y-1.5 w-0.5 bg-text" :style="{ left: `${Math.min(100, timePct)}%` }" aria-hidden="true" />
  </div>
</template>

<script setup>
import { computed } from 'vue'

// 預算進度條（SA FR-5.4、5.5）：實線為已花費，斜紋為「依目前速度推估」的月底花費，直線為本月已過的時間比例
const props = defineProps({
  percentage: { type: [String, Number], required: true },
  projected: { type: [String, Number], default: null },
  tone: { type: String, default: 'inc' },
  timePct: { type: Number, default: null },
  thick: Boolean,
  /** 未達門檻時用主色（總預算） */
  accent: Boolean
})
const FILL = { inc: 'bg-inc', warn: 'bg-warn', exp: 'bg-exp', accent: 'bg-accent' }
const fill = computed(() => FILL[props.tone === 'inc' && props.accent ? 'accent' : props.tone] ?? 'bg-inc')
const spentW = computed(() => Math.min(100, Math.max(0, Number(props.percentage))))
const projW = computed(() => props.projected == null ? 0 : Math.max(0, Math.min(100, Number(props.projected)) - spentW.value))
</script>
