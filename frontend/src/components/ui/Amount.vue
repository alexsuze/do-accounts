<template>
  <span class="num whitespace-nowrap" :class="tone">{{ text }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { formatAmount, signOf } from '../../utils/money'

// 金額顯示（SD 5.5）：等寬字、正負號、收支顏色。kind=transfer 時用灰色與 ⇄
const props = defineProps({
  value: { type: [String, Number], default: null },
  currency: { type: String, default: 'TWD' },
  kind: { type: String, default: '' },
  sign: { type: String, default: 'always' },
  symbol: Boolean,
  plain: Boolean
})
const text = computed(() => {
  if (props.kind === 'transfer') return '⇄ ' + formatAmount(props.value, props.currency, { sign: 'none', symbol: props.symbol })
  return formatAmount(props.value, props.currency, { sign: props.sign, symbol: props.symbol })
})
const tone = computed(() => {
  if (props.kind === 'transfer') return 'text-muted'
  if (props.plain) return 'text-text'
  const s = signOf(props.value)
  return s > 0 ? 'text-inc' : s < 0 ? 'text-exp' : ''
})
</script>
