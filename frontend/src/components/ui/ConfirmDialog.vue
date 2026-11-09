<template>
  <AlertDialogRoot :open="open" @update:open="v => emit('update:open', v)">
    <AlertDialogPortal>
      <AlertDialogOverlay class="fixed inset-0 z-50 bg-scrim" />
      <AlertDialogContent
        class="fixed top-1/2 left-1/2 z-50 flex w-[min(440px,calc(100vw-32px))] -translate-x-1/2 -translate-y-1/2 flex-col gap-4 rounded-[18px] border border-line bg-panel p-6 text-text glow">
        <AlertDialogTitle class="font-head text-lg font-semibold">{{ title }}</AlertDialogTitle>
        <AlertDialogDescription class="text-sm leading-relaxed text-muted">{{ description }}</AlertDialogDescription>
        <div class="flex justify-end gap-2">
          <AlertDialogCancel as-child><AppButton variant="ghost">取消</AppButton></AlertDialogCancel>
          <AlertDialogAction as-child>
            <AppButton :variant="danger ? 'danger' : 'primary'" @click="emit('confirm')">{{ confirmText }}</AppButton>
          </AlertDialogAction>
        </div>
      </AlertDialogContent>
    </AlertDialogPortal>
  </AlertDialogRoot>
</template>

<script setup>
import { AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogOverlay, AlertDialogPortal, AlertDialogRoot, AlertDialogTitle } from 'reka-ui'
import AppButton from './AppButton.vue'

// 刪除、停用等需要確認的動作（SD 5.5）
defineProps({
  open: Boolean,
  title: { type: String, required: true },
  description: { type: String, default: '' },
  confirmText: { type: String, default: '確定' },
  danger: Boolean
})
const emit = defineEmits(['update:open', 'confirm'])
</script>
