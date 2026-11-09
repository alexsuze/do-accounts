<template>
  <div class="relative shrink-0" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" role="img" :aria-label="label">
      <circle :cx="c" :cy="c" :r="r" fill="none" stroke="var(--panel2)" :stroke-width="thickness" />
      <circle v-for="(s, i) in segments" :key="i" :cx="c" :cy="c" :r="r" fill="none" :stroke="items[i].color"
        :stroke-width="thickness" :stroke-dasharray="s.dash" :stroke-dashoffset="s.offset" :transform="`rotate(-90 ${c} ${c})`">
        <title>{{ items[i].label }}</title>
      </circle>
    </svg>
    <div class="absolute inset-0 flex flex-col items-center justify-center"><slot /></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { donutSegments } from '../../utils/charts'

// 環形圖（收支結構）。items：[{ label, value, color }]
const props = defineProps({
  items: { type: Array, required: true },
  label: { type: String, required: true },
  size: { type: Number, default: 200 },
  thickness: { type: Number, default: 26 }
})
const c = computed(() => props.size / 2)
const r = computed(() => props.size / 2 - props.thickness / 2 - 4)
const segments = computed(() => donutSegments(props.items.map(i => Number(i.value)), 2 * Math.PI * r.value))
</script>
