<template>
  <component :is="to ? RouterLink : 'button'" :to="to" :type="to ? undefined : type" :disabled="to ? undefined : (disabled || loading)"
    :class="[base, sizes[size], variants[variant], (disabled || loading) && 'opacity-50']">
    <span v-if="loading" class="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden="true" />
    <slot />
  </component>
</template>

<script setup>
import { RouterLink } from 'vue-router'

// 按鈕（SD 5.5）：primary 青底、ghost 框線、danger 紅框；md 44px、sm 36px
defineProps({
  variant: { type: String, default: 'primary' },
  size: { type: String, default: 'md' },
  type: { type: String, default: 'button' },
  to: { type: [String, Object], default: null },
  disabled: Boolean,
  loading: Boolean
})

const base = 'inline-flex items-center justify-center gap-2 rounded-[10px] font-medium whitespace-nowrap no-underline transition-colors'
const sizes = { md: 'h-11 px-4 text-sm', sm: 'h-9 px-3 text-[13px]' }
const variants = {
  primary: 'bg-accent text-accent-ink font-semibold hover:brightness-110',
  ghost: 'border border-line bg-panel text-text hover:bg-panel2',
  danger: 'border border-exp bg-transparent text-exp hover:bg-exp-soft',
  plain: 'bg-transparent text-muted hover:text-text hover:bg-panel2'
}
</script>
