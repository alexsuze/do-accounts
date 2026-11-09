<template>
  <FormField v-slot="f" :label="label" :hint="hint" :error="error" :required="required">
    <input :id="f.id" :value="modelValue" :type="type" :placeholder="placeholder" :maxlength="maxlength" :min="min" :max="max"
      :inputmode="inputmode" :autocomplete="autocomplete" :aria-invalid="f.invalid" :aria-required="f.required || undefined" :aria-describedby="f.describedby"
      :class="['h-11 w-full rounded-[10px] border bg-panel2 px-3 text-sm text-text outline-none placeholder:text-muted focus:border-accent', f.invalid ? 'border-exp' : 'border-line', mono && 'num', align === 'right' && 'text-right']"
      @input="e => emit('update:modelValue', e.target.value)" @blur="emit('blur')">
  </FormField>
</template>

<script setup>
import FormField from './FormField.vue'

defineProps({
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  maxlength: { type: Number, default: undefined },
  min: { type: String, default: undefined },
  max: { type: String, default: undefined },
  inputmode: { type: String, default: undefined },
  autocomplete: { type: String, default: 'off' },
  align: { type: String, default: 'left' },
  mono: Boolean,
  required: Boolean
})
const emit = defineEmits(['update:modelValue', 'blur'])
</script>
