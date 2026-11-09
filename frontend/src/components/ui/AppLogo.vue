<template>
  <span class="flex items-center gap-2.5">
    <svg :width="size" :height="size" viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <rect x="1" y="1" width="30" height="30" rx="8" stroke="var(--accent)" stroke-width="2" />
      <path d="M9 21V11h5a5 5 0 0 1 0 10H9Z" stroke="var(--accent)" stroke-width="2" />
      <circle cx="23" cy="16" r="2" fill="var(--accent)" />
    </svg>
    <span v-if="withText" class="font-head font-bold tracking-wide" :style="{ fontSize: size * 0.6 + 'px' }">do-accounts</span>
  </span>
</template>

<script setup>
defineProps({ size: { type: Number, default: 30 }, withText: { type: Boolean, default: true } })
</script>
