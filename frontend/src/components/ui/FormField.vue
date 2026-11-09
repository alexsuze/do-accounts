<template>
  <div class="flex min-w-0 flex-col gap-1.5">
    <span v-if="label" class="flex gap-1">
      <label :for="id" class="lbl">{{ label }}</label><span v-if="required" class="lbl text-exp" aria-hidden="true">*</span>
    </span>
    <slot :id="id" :invalid="!!error" :required="required" :describedby="error ? `${id}-err` : hint ? `${id}-hint` : undefined" />
    <span v-if="error" :id="`${id}-err`" role="alert" class="text-xs text-exp">{{ error }}</span>
    <span v-else-if="hint" :id="`${id}-hint`" class="text-xs text-muted">{{ hint }}</span>
  </div>
</template>

<script setup>
import { useId } from 'vue'

// 欄位外框：標籤、提示、錯誤訊息（SD 5.8：錯誤標在欄位下方，role=alert）
defineProps({
  label: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  required: Boolean
})
const id = useId()
</script>
