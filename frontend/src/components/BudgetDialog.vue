<template>
  <AppDialog :open="open" :title="item ? '編輯預算' : '新增預算'" :description="`${month} · 金額以 ${currency} 計`" :width="480" @update:open="v => emit('update:open', v)">
    <form id="budget-form" class="flex flex-col gap-4" novalidate @submit.prevent="save">
      <SelectField v-model="f.category" label="分類" :options="options" :disabled="!!item" required :error="errors.category"
        hint="大類的預算包含子類的花費" />
      <MoneyInput v-model="f.amount" :currency="currency" label="每月金額" :error="errors.amount" required />
      <label class="flex min-h-11 cursor-pointer items-center gap-3">
        <span class="mr-auto flex flex-col gap-0.5">
          <span>每月循環</span>
          <span class="text-xs text-muted">之後沒有另外設定的月份都沿用這個金額</span>
        </span>
        <SwitchField v-model="f.recurring" label="每月循環" />
      </label>
      <p v-if="item?.inherited" class="m-0 rounded-[10px] border border-dashed border-line px-3.5 py-2.5 text-[13px] text-muted">
        這項是沿用 {{ item.source_month }} 設定的循環預算。儲存後會從 {{ month }} 起改用新的金額，之前的月份不變。
      </p>
    </form>
    <template #footer>
      <AppButton v-if="item" variant="danger" :loading="busy === 'remove'" @click="remove">從本月起移除</AppButton>
      <AppButton variant="ghost" class="ml-auto" @click="emit('update:open', false)">取消</AppButton>
      <AppButton type="submit" form="budget-form" :loading="busy === 'save'">儲存</AppButton>
    </template>
  </AppDialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import AppButton from './ui/AppButton.vue'
import AppDialog from './ui/AppDialog.vue'
import MoneyInput from './ui/MoneyInput.vue'
import SelectField from './ui/SelectField.vue'
import SwitchField from './ui/SwitchField.vue'
import api from '../services/api'
import { useCategoriesStore } from '../stores/categories'
import { toast } from '../stores/toast'
import { categoryOptions } from './options'
import { digitsOf } from '../utils/money'
import { decimalsError, toAmountString } from '../utils/expr'

// 新增／編輯預算（SA FR-5.1、5.3）。總預算的分類值用 TOTAL（Reka Select 不接受空字串）
const TOTAL = '__total__'
const props = defineProps({
  open: Boolean,
  month: { type: String, required: true },
  currency: { type: String, default: 'TWD' },
  /** 編輯中的項目（BudgetItem）；null 為新增 */
  item: { type: Object, default: null },
  /** 新增時預先選好的分類（「未設預算的支出」的設定按鈕） */
  preset: { type: String, default: null },
  /** 本月已經有預算的分類 id（新增時不能重複選；null 代表總預算） */
  taken: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:open', 'saved'])
const categories = useCategoriesStore()
const f = reactive({ category: null, amount: '', recurring: true })
const errors = reactive({})
const busy = ref('')

const options = computed(() => {
  const taken = new Set(props.taken.map(id => id ?? TOTAL))
  const groups = categoryOptions(categories.tree('expense')).map(g => ({
    ...g, options: g.options.map(o => ({ ...o, disabled: !props.item && taken.has(o.value) }))
  }))
  return [{ label: '整體', options: [{ value: TOTAL, label: '本月總預算', disabled: !props.item && taken.has(TOTAL) }] }, ...groups]
})

watch(() => props.open, (v) => {
  if (!v) return
  Object.keys(errors).forEach(k => delete errors[k])
  const it = props.item
  Object.assign(f, {
    category: it ? (it.category_id ?? TOTAL) : props.preset,
    amount: it ? String(it.amount) : '',
    recurring: it ? it.recurring : true
  })
})

async function save () {
  Object.keys(errors).forEach(k => delete errors[k])
  if (!f.category) errors.category = '請選擇分類'
  let amount = null
  try {
    amount = toAmountString(f.amount, digitsOf(props.currency))
    const dec = decimalsError(f.amount, digitsOf(props.currency), props.currency)
    if (amount === null) errors.amount = '請輸入金額'
    else if (dec) errors.amount = dec
    else if (!(Number(amount) > 0)) errors.amount = '金額必須大於 0'
  } catch (e) { errors.amount = e.message }
  if (Object.keys(errors).length) return
  busy.value = 'save'
  try {
    await api.put('/budgets', { month: props.month, category_id: f.category === TOTAL ? null : f.category, amount, recurring: f.recurring })
    toast.success('預算已儲存')
    emit('saved')
    emit('update:open', false)
  } finally {
    busy.value = ''
  }
}

async function remove () {
  busy.value = 'remove'
  try {
    await api.delete('/budgets', { params: { month: props.month, category_id: props.item.category_id ?? undefined } })
    toast.success(`已從 ${props.month} 起移除這項預算`)
    emit('saved')
    emit('update:open', false)
  } finally {
    busy.value = ''
  }
}
</script>
