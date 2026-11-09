<template>
  <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
    <TextField v-model="f.name" label="名稱" :maxlength="30" :error="errors.name" required />
    <div class="grid gap-3 sm:grid-cols-2">
      <SelectField v-model="f.kind" label="類型" :options="ACCOUNT_KINDS" required />
      <SelectField v-model="f.currency" label="幣別" :options="CURRENCY_OPTIONS" :disabled="account?.has_transactions" required
        :hint="account?.has_transactions ? '已有交易，不能變更幣別' : ''" />
    </div>
    <div class="grid gap-3 sm:grid-cols-2">
      <MoneyInput v-model="f.opening_balance" :currency="f.currency" label="初始餘額" :error="errors.opening_balance"
        :hint="liability ? '欠款請填負數，例如 -18330' : account?.has_transactions ? '修改後餘額與趨勢自動重算' : ''" />
      <DateField v-model="f.opening_date" label="開戶日期" :max="earliest" :error="errors.opening_date" required
        :hint="earliest ? `不可晚於最早交易 ${earliest}` : '交易日期不可早於這一天'" />
    </div>
    <div v-if="f.kind === 'credit_card'" class="grid gap-3 sm:grid-cols-3">
      <MoneyInput v-model="f.credit.limit" :currency="f.currency" label="信用額度" />
      <TextField v-model="f.credit.statement_day" label="結帳日" type="number" min="1" max="31" inputmode="numeric" mono placeholder="1–31" />
      <TextField v-model="f.credit.due_day" label="繳款日" type="number" min="1" max="31" inputmode="numeric" mono placeholder="1–31" />
    </div>
    <TextField v-model="f.note" label="備註" :maxlength="200" placeholder="選填" />
    <div v-if="account" class="num flex flex-col gap-1.5 rounded-[10px] border border-dashed border-line px-3.5 py-3 text-[13px]">
      <div class="flex justify-between"><span class="text-muted">初始餘額</span><span>{{ formatAmount(account.opening_balance, account.currency) }}</span></div>
      <div class="flex justify-between"><span class="text-muted">＋ 交易加總</span><span>{{ formatAmount(account.balance - account.opening_balance, account.currency, { sign: 'always' }) }}</span></div>
      <div class="flex justify-between border-t border-line pt-1.5 font-semibold"><span>＝ 目前餘額</span><span>{{ formatAmount(account.balance, account.currency) }}</span></div>
    </div>
    <div class="flex flex-wrap gap-2.5 border-t border-line pt-4">
      <template v-if="account">
        <AppButton variant="ghost" @click="emit('archive')"><Archive :size="16" aria-hidden="true" />{{ account.archived_at ? '取消封存' : '封存' }}</AppButton>
        <AppButton variant="danger" :disabled="account.has_transactions" :title="account.has_transactions ? '有交易的帳戶不能刪除，請改用封存' : ''" @click="emit('delete')">刪除</AppButton>
      </template>
      <span class="mr-auto" />
      <AppButton variant="ghost" @click="emit('cancel')">取消</AppButton>
      <AppButton type="submit" :loading="saving">{{ account ? '儲存' : '新增帳戶' }}</AppButton>
    </div>
  </form>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Archive } from 'lucide-vue-next'
import AppButton from './ui/AppButton.vue'
import DateField from './ui/DateField.vue'
import MoneyInput from './ui/MoneyInput.vue'
import SelectField from './ui/SelectField.vue'
import TextField from './ui/TextField.vue'
import api from '../services/api'
import { ACCOUNT_KINDS, CURRENCY_OPTIONS } from '../utils/labels'
import { digitsOf, formatAmount } from '../utils/money'
import { toAmountString } from '../utils/expr'
import { today } from '../utils/date'

// 帳戶表單（SA FR-2.1、2.2、2.5、2.8）：新增與編輯共用
const props = defineProps({ account: { type: Object, default: null }, saving: Boolean })
const emit = defineEmits(['submit', 'cancel', 'archive', 'delete'])
const f = reactive({})
const errors = reactive({})
const earliest = ref(undefined)
const liability = computed(() => f.kind === 'credit_card' || f.kind === 'loan')

watch(() => props.account, async (a) => {
  Object.keys(errors).forEach(k => delete errors[k])
  Object.assign(f, {
    name: a?.name ?? '', kind: a?.kind ?? 'bank', currency: a?.currency ?? 'TWD',
    opening_balance: a ? String(a.opening_balance) : '0', opening_date: a?.opening_date ?? today(),
    visible: a?.visible ?? true, note: a?.note ?? '',
    credit: { limit: a?.credit?.limit != null ? String(a.credit.limit) : '', statement_day: a?.credit?.statement_day ?? '', due_day: a?.credit?.due_day ?? '' }
  })
  earliest.value = undefined
  if (a?.has_transactions) {
    // 最早交易日期：帳戶交易清單的最後一筆（新到舊）
    const { data } = await api.get(`/accounts/${a.id}/ledger`, { params: { size: 1 }, silent: true }).catch(() => ({ data: null }))
    if (data?.total) {
      const last = await api.get(`/accounts/${a.id}/ledger`, { params: { size: 1, page: data.total - 1 }, silent: true }).catch(() => null)
      earliest.value = last?.data?.items?.[0]?.transaction?.date
    }
  }
}, { immediate: true })

function submit () {
  Object.keys(errors).forEach(k => delete errors[k])
  if (!f.name.trim()) errors.name = '請輸入名稱'
  let opening = '0'
  try { opening = toAmountString(f.opening_balance || '0', digitsOf(f.currency)) } catch (e) { errors.opening_balance = e.message }
  if (!f.opening_date) errors.opening_date = '請選擇開戶日期'
  else if (earliest.value && f.opening_date > earliest.value) errors.opening_date = `不可晚於最早交易 ${earliest.value}`
  if (Object.keys(errors).length) return
  const num = (v) => v === '' || v == null ? null : Number(v)
  emit('submit', {
    name: f.name.trim(), kind: f.kind, currency: f.currency, opening_balance: opening, opening_date: f.opening_date,
    visible: f.visible, note: f.note,
    credit: f.kind === 'credit_card' ? { limit: f.credit.limit ? toAmountString(f.credit.limit, digitsOf(f.currency)) : null, statement_day: num(f.credit.statement_day), due_day: num(f.credit.due_day) } : null
  })
}
</script>
