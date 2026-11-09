<template>
  <FormField v-slot="f" :label="label" :hint="hint" :error="error || exprError" :required="required">
    <span :class="['flex items-center gap-2.5 rounded-[12px] border-2 bg-panel2 px-4', large ? 'h-16' : 'h-11 border', (f.invalid || exprError) ? 'border-exp' : focused ? 'border-accent' : 'border-line', large && focused && 'glow']">
      <span class="num shrink-0 text-muted" :class="large ? 'text-base' : 'text-sm'">{{ symbol }}</span>
      <input :id="f.id" ref="inputEl" :value="modelValue" inputmode="decimal" autocomplete="off" :aria-invalid="f.invalid || !!exprError" :aria-required="f.required || undefined"
        :aria-describedby="f.describedby" :placeholder="placeholder"
        :class="['num min-w-0 flex-1 border-0 bg-transparent text-text outline-none placeholder:text-muted', large ? 'text-[28px] font-semibold' : 'text-base text-right']"
        @input="e => emit('update:modelValue', e.target.value)" @focus="focused = true" @blur="onBlur">
      <span v-if="preview" class="num shrink-0 text-accent" :class="large ? 'text-lg' : 'text-sm'" aria-live="polite">= {{ preview }}</span>
      <button type="button" aria-label="開啟計算機" class="flex size-10 shrink-0 items-center justify-center rounded-[10px] border border-line bg-panel text-text hover:bg-panel2"
        @click="ui.openCalculator(v => emit('update:modelValue', v), modelValue)">
        <Calculator :size="16" aria-hidden="true" />
      </button>
    </span>
  </FormField>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Calculator } from 'lucide-vue-next'
import FormField from './FormField.vue'
import { useUiStore } from '../../stores/ui'
import { CURRENCIES, digitsOf, formatAmount } from '../../utils/money'
import { evaluate, isExpression, toAmountString } from '../../utils/expr'

// 金額輸入（SD 5.5）：可直接輸入算式（120+35），失焦時換成結果；也可開全域計算機帶入
const props = defineProps({
  modelValue: { type: String, default: '' },
  currency: { type: String, default: 'TWD' },
  label: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  placeholder: { type: String, default: '0' },
  large: Boolean,
  required: Boolean
})
const emit = defineEmits(['update:modelValue'])
const ui = useUiStore()
const focused = ref(false)
const inputEl = ref(null)
const symbol = computed(() => CURRENCIES[props.currency]?.symbol ?? props.currency)

const exprError = computed(() => {
  try { evaluate(props.modelValue); return '' } catch (e) { return e.message }
})
const preview = computed(() => {
  if (!isExpression(props.modelValue) || exprError.value) return ''
  try { return formatAmount(toAmountString(props.modelValue, digitsOf(props.currency)), props.currency) } catch { return '' }
})

function onBlur () {
  focused.value = false
  if (isExpression(props.modelValue) && !exprError.value) emit('update:modelValue', toAmountString(props.modelValue, digitsOf(props.currency)))
}

defineExpose({ focus: () => inputEl.value?.focus() })
</script>
