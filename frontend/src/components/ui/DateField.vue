<template>
  <TextField :model-value="modelValue" type="date" mono :label="label" :hint="hint" :error="error" :min="min" :max="max" :required="required"
    @update:model-value="v => emit('update:modelValue', v)" />
</template>

<script setup>
import TextField from './TextField.vue'

// 日期欄位：使用瀏覽器原生日期選擇（手機有系統滾輪、可鍵盤輸入），min 用來擋開戶日期之前（SA FR-2.7）
defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  min: { type: String, default: undefined },
  max: { type: String, default: undefined },
  required: Boolean
})
const emit = defineEmits(['update:modelValue'])
</script>
