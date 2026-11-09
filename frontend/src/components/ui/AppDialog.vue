<template>
  <DialogRoot :open="open" @update:open="v => emit('update:open', v)">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 z-50 bg-scrim" />
      <DialogContent
        class="glow fixed z-50 flex max-h-dvh flex-col border-line bg-panel text-text sm:top-1/2 sm:left-1/2 sm:max-h-[calc(100dvh-48px)] sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-[18px] sm:border max-sm:inset-0"
        :style="{ width: `min(${width}px, 100vw)` }" @open-auto-focus="e => noAutoFocus && e.preventDefault()">
        <div class="flex items-center gap-3 border-b border-line px-5 py-4">
          <DialogTitle class="mr-auto font-head text-xl font-bold">{{ title }}</DialogTitle>
          <slot name="header-actions" />
          <DialogClose as-child><IconButton label="關閉"><X :size="16" aria-hidden="true" /></IconButton></DialogClose>
        </div>
        <DialogDescription :class="description ? 'px-5 pt-3 text-sm text-muted' : 'sr-only'">{{ description || title }}</DialogDescription>
        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4"><slot /></div>
        <div v-if="$slots.footer" class="flex flex-wrap items-center gap-3 border-t border-line px-5 py-4"><slot name="footer" /></div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<script setup>
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import { X } from 'lucide-vue-next'
import IconButton from './IconButton.vue'

// 對話框（SD 5.5）：桌面置中、手機全螢幕
defineProps({
  open: Boolean,
  title: { type: String, required: true },
  description: { type: String, default: '' },
  width: { type: Number, default: 600 },
  noAutoFocus: Boolean
})
const emit = defineEmits(['update:open'])
</script>
