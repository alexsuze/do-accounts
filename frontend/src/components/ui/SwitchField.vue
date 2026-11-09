<template>
  <SwitchRoot :model-value="modelValue" :aria-label="label" :disabled="disabled"
    class="relative inline-flex h-[26px] w-11 shrink-0 items-center rounded-full border border-line bg-panel2 transition-colors data-[state=checked]:border-accent data-[state=checked]:bg-accent"
    @update:model-value="v => emit('update:modelValue', v)">
    <SwitchThumb class="block size-[18px] translate-x-[3px] rounded-full bg-muted transition-transform data-[state=checked]:translate-x-[21px] data-[state=checked]:bg-accent-ink" />
  </SwitchRoot>
</template>

<script setup>
import { SwitchRoot, SwitchThumb } from 'reka-ui'

// 開關（Reka UI Switch）。外框 44px 寬，點擊範圍由外層 label 補足
defineProps({ modelValue: Boolean, label: { type: String, required: true }, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
</script>
