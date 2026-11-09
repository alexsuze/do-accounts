<template>
  <button :type="type" :aria-label="label" :title="label"
    class="inline-flex size-11 shrink-0 items-center justify-center rounded-[10px] border border-line bg-panel text-text transition-colors hover:bg-panel2">
    <slot />
  </button>
</template>

<script setup>
// 只有圖示的按鈕：label 必填，作為 aria-label（SD 5.8）
defineProps({
  label: { type: String, required: true },
  type: { type: String, default: 'button' }
})
</script>
