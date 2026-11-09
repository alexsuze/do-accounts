<template>
  <FormField v-slot="f" :label="label" :hint="hint" :error="error" :required="required">
    <SelectRoot :model-value="modelValue ?? NONE" :disabled="disabled" @update:model-value="v => emit('update:modelValue', v === NONE ? null : v)">
      <SelectTrigger :id="f.id" :aria-invalid="f.invalid" :aria-required="f.required || undefined" :aria-describedby="f.describedby" :aria-label="label ? undefined : placeholder"
        :class="['flex h-11 w-full min-w-0 items-center gap-2 rounded-[10px] border bg-panel2 px-3 text-left text-sm text-text outline-none focus:border-accent data-[disabled]:opacity-60', f.invalid ? 'border-exp' : 'border-line']">
        <SelectValue :placeholder="placeholder" class="min-w-0 flex-1 truncate data-[placeholder]:text-muted" />
        <SelectIcon><ChevronDown :size="16" class="text-muted" aria-hidden="true" /></SelectIcon>
      </SelectTrigger>
      <SelectPortal>
        <SelectContent position="popper" :side-offset="4"
          class="z-[60] max-h-[min(360px,var(--reka-select-content-available-height))] w-[var(--reka-select-trigger-width)] min-w-[180px] overflow-hidden rounded-[12px] border border-line bg-panel text-sm text-text shadow-xl">
          <SelectViewport class="p-1">
            <template v-for="g in normalized" :key="g.label || 'flat'">
              <SelectGroup>
                <SelectLabel v-if="g.label" class="lbl px-3 pt-2 pb-1">{{ g.label }}</SelectLabel>
                <SelectItem v-for="o in g.options" :key="o.value" :value="o.value" :disabled="o.disabled"
                  class="relative flex min-h-10 cursor-pointer items-center rounded-lg py-2 pr-8 pl-3 outline-none select-none data-[disabled]:opacity-40 data-[highlighted]:bg-accent-soft data-[highlighted]:text-accent"
                  :class="o.indent && 'pl-7'">
                  <SelectItemText>{{ o.label }}</SelectItemText>
                  <SelectItemIndicator class="absolute right-2"><Check :size="14" aria-hidden="true" /></SelectItemIndicator>
                </SelectItem>
              </SelectGroup>
            </template>
          </SelectViewport>
        </SelectContent>
      </SelectPortal>
    </SelectRoot>
  </FormField>
</template>

<script setup>
import { computed } from 'vue'
import { SelectContent, SelectGroup, SelectIcon, SelectItem, SelectItemIndicator, SelectItemText, SelectLabel, SelectPortal, SelectRoot, SelectTrigger, SelectValue, SelectViewport } from 'reka-ui'
import { Check, ChevronDown } from 'lucide-vue-next'
import FormField from './FormField.vue'

// 下拉選單（Reka UI Select，SD 5.5）。options：[{ value, label, disabled?, indent? }] 或分組 [{ label, options }]
// value 不能是空字串（Reka 限制），「全部 / 不選」用 null
const NONE = '__none__'
const props = defineProps({
  modelValue: { type: String, default: null },
  options: { type: Array, required: true },
  label: { type: String, default: '' },
  placeholder: { type: String, default: '請選擇' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  noneLabel: { type: String, default: '' },
  disabled: Boolean,
  required: Boolean
})
const emit = defineEmits(['update:modelValue'])

const normalized = computed(() => {
  const grouped = props.options.length && props.options[0].options !== undefined
  const groups = grouped ? props.options : [{ label: '', options: props.options }]
  return props.noneLabel ? [{ label: '', options: [{ value: NONE, label: props.noneLabel }] }, ...groups] : groups
})
</script>
