<template>
  <component :is="as" :class="['rounded-[14px] border bg-panel', glow ? 'border-accent glow' : 'border-line', padded && 'p-5']">
    <slot />
  </component>
</template>

<script setup>
defineProps({
  as: { type: String, default: 'section' },
  glow: Boolean,
  padded: { type: Boolean, default: true }
})
</script>
