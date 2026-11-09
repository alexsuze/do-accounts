<template>
  <ToggleGroupRoot :model-value="modelValue" type="single" :aria-label="label"
    class="inline-flex gap-1 rounded-xl border border-line bg-panel2 p-1"
    @update:model-value="v => v && emit('update:modelValue', v)">
    <ToggleGroupItem v-for="o in options" :key="o.value" :value="o.value"
      class="h-9 rounded-lg px-3.5 text-sm text-muted transition-colors data-[state=on]:bg-accent data-[state=on]:font-semibold data-[state=on]:text-accent-ink">
      {{ o.label }}
    </ToggleGroupItem>
  </ToggleGroupRoot>
</template>

<script setup>
import { ToggleGroupItem, ToggleGroupRoot } from 'reka-ui'

// 分段切換（SD 5.5）。options：[{ value, label }]；不允許取消選取
defineProps({
  modelValue: { type: String, required: true },
  options: { type: Array, required: true },
  label: { type: String, required: true }
})
const emit = defineEmits(['update:modelValue'])
</script>
