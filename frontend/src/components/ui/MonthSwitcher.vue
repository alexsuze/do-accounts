<template>
  <div class="flex items-center gap-1.5">
    <IconButton label="上個月" @click="emit('update:modelValue', shiftMonth(modelValue, -1))"><ChevronLeft :size="18" aria-hidden="true" /></IconButton>
    <span class="num flex h-11 min-w-[120px] items-center justify-center gap-2 rounded-[10px] border border-accent bg-accent-soft px-3.5 font-semibold text-accent" aria-live="polite">
      {{ modelValue }}<span v-if="modelValue === current" class="text-[11px] font-normal">本月</span>
    </span>
    <IconButton label="下個月" @click="emit('update:modelValue', shiftMonth(modelValue, 1))"><ChevronRight :size="18" aria-hidden="true" /></IconButton>
  </div>
</template>

<script setup>
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import IconButton from './IconButton.vue'
import { shiftMonth, thisMonth } from '../../utils/date'

defineProps({ modelValue: { type: String, required: true } })
const emit = defineEmits(['update:modelValue'])
const current = thisMonth()
</script>
