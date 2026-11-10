<template>
  <AppDialog :open="open" title="鍵盤快捷鍵" :width="440" @update:open="v => emit('update:open', v)">
    <ul class="m-0 flex list-none flex-col p-0">
      <li v-for="s in SHORTCUTS" :key="s.label" class="flex min-h-11 items-center gap-3 border-b border-line last:border-b-0">
        <span class="mr-auto text-sm">{{ s.label }}</span>
        <span class="flex gap-1">
          <kbd v-for="k in s.keys" :key="k" class="num min-w-7 rounded-md border border-line bg-panel2 px-1.5 py-0.5 text-center text-xs">{{ k }}</kbd>
        </span>
      </li>
    </ul>
  </AppDialog>
</template>

<script setup>
import AppDialog from './ui/AppDialog.vue'
import { SHORTCUTS } from '../utils/shortcuts'

defineProps({ open: Boolean })
const emit = defineEmits(['update:open'])
</script>
