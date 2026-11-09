<template>
  <AppCard :glow="glow" class="flex flex-col gap-2">
    <span class="lbl">{{ label }}</span>
    <span class="num text-[26px] leading-tight font-semibold" :class="toneClass"><slot>{{ value }}</slot></span>
    <span v-if="hint" class="num text-xs text-muted">{{ hint }}</span>
  </AppCard>
</template>

<script setup>
import { computed } from 'vue'
import AppCard from './AppCard.vue'

// 小標籤＋大數字＋說明（SD 5.5）。tone：inc / exp / warn / 空字串
const props = defineProps({
  label: { type: String, required: true },
  value: { type: String, default: '' },
  hint: { type: String, default: '' },
  tone: { type: String, default: '' },
  glow: Boolean
})
const toneClass = computed(() => ({ inc: 'text-inc', exp: 'text-exp', warn: 'text-warn' })[props.tone] || '')
</script>
