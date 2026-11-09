<template>
  <ToastProvider swipe-direction="right">
    <ToastRoot v-for="t in toasts" :key="t.id" :duration="t.duration" default-open
      class="flex items-start gap-3 rounded-xl border bg-panel px-4 py-3 text-sm text-text shadow-lg"
      :class="tone[t.type].border" @update:open="o => !o && dismiss(t.id)">
      <component :is="tone[t.type].icon" :size="18" class="mt-0.5 shrink-0" :class="tone[t.type].text" aria-hidden="true" />
      <ToastDescription class="flex-1 leading-relaxed">{{ t.message }}</ToastDescription>
      <ToastClose aria-label="關閉通知" class="text-muted hover:text-text"><X :size="16" aria-hidden="true" /></ToastClose>
    </ToastRoot>
    <ToastViewport class="fixed top-4 right-4 z-[100] flex w-[min(380px,calc(100vw-32px))] flex-col gap-2 outline-none" />
  </ToastProvider>
</template>

<script setup>
import { ToastClose, ToastDescription, ToastProvider, ToastRoot, ToastViewport } from 'reka-ui'
import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from 'lucide-vue-next'
import { dismiss, toasts } from '../../stores/toast'

// 右上角通知（SD 5.5）；api.js 失敗時、store 成功時呼叫 toast.*
const tone = {
  success: { border: 'border-inc', text: 'text-inc', icon: CircleCheck },
  error: { border: 'border-exp', text: 'text-exp', icon: CircleAlert },
  warn: { border: 'border-warn', text: 'text-warn', icon: TriangleAlert },
  info: { border: 'border-accent', text: 'text-accent', icon: Info }
}
</script>
