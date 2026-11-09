<template>
  <span class="num inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] whitespace-nowrap" :class="tones[tone]">
    <slot />
  </span>
</template>

<script setup>
// 狀態標籤：accent / inc / exp / warn / muted
defineProps({ tone: { type: String, default: 'muted' } })
const tones = {
  accent: 'border-accent text-accent',
  inc: 'border-inc text-inc',
  exp: 'border-exp text-exp',
  warn: 'border-warn text-warn',
  muted: 'border-line text-muted'
}
</script>
