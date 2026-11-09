<template>
  <div class="flex flex-col gap-5">
    <header class="flex flex-wrap items-center gap-3">
      <IconButton label="返回帳戶" @click="router.push('/accounts')"><ChevronLeft :size="18" aria-hidden="true" /></IconButton>
      <div class="mr-auto flex flex-col gap-1">
        <span class="lbl">帳戶 › {{ kindLabel(account?.kind) }}</span>
        <h1 class="m-0 flex items-center gap-2.5 font-head text-[28px] leading-tight font-bold">{{ account?.name ?? '…' }}
          <span v-if="account" class="num rounded-md border border-line px-2 py-0.5 text-xs font-normal text-muted">{{ account.currency }}</span>
          <Badge v-if="account?.archived_at">已封存</Badge>
        </h1>
      </div>
      <ThemeToggle />
      <AppButton variant="ghost" :to="{ path: '/accounts', query: { edit: id } }">編輯帳戶</AppButton>
      <AppButton v-if="account && !account.archived_at" @click="ui.openTx({ preset: { account_id: id } })"><Plus :size="16" aria-hidden="true" />記一筆</AppButton>
    </header>

    <template v-if="account">
      <section class="grid grid-cols-[repeat(auto-fit,minmax(210px,1fr))] gap-3">
        <StatCard glow label="目前餘額" :value="formatAmount(account.balance, account.currency)"
          :hint="`初始 ${formatAmount(account.opening_balance, account.currency)}（${account.opening_date}）＋ 交易 ${formatAmount(account.balance - account.opening_balance, account.currency, { sign: 'always' })}`" />
        <StatCard label="本月流入" :value="formatAmount(month.in, account.currency, { sign: 'always' })" tone="inc" :hint="`${month.inCount} 筆`" />
        <StatCard label="本月流出" :value="formatAmount(-month.out, account.currency, { sign: 'always' })" tone="exp" :hint="`${month.outCount} 筆`" />
        <StatCard label="交易筆數" :value="String(ledger?.total ?? 0)" />
      </section>

      <AppCard class="flex flex-col gap-3">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="m-0 mr-auto font-head text-base font-semibold">每日餘額</h2>
          <SegmentedControl v-model="days" :options="rangeOptions" label="期間" />
        </div>
        <BalanceChart v-if="history.length" :points="history" :label="`近 ${days} 天每日餘額`" />
        <div v-if="history.length" class="num flex justify-between text-[11px] text-muted"><span>{{ history[0].date }}</span><span>{{ history.at(-1).date }}</span></div>
        <p v-else class="m-0 text-sm text-muted">這段期間還沒開戶。</p>
      </AppCard>

      <AppCard :padded="false" class="overflow-hidden">
        <div class="flex items-center border-b border-line px-5 py-3.5"><h2 class="m-0 mr-auto font-head text-base font-semibold">此帳戶的交易</h2>
          <RouterLink :to="{ path: '/transactions', query: { account: id } }" class="text-[13px]">在明細中篩選 →</RouterLink></div>
        <div class="overflow-x-auto">
          <table class="w-full min-w-[640px] border-collapse text-sm">
            <thead><tr class="bg-panel2 text-left">
              <th class="lbl border-b border-line px-4 py-2.5 font-medium">日期</th><th class="lbl border-b border-line px-4 py-2.5 font-medium">項目</th>
              <th class="lbl border-b border-line px-4 py-2.5 font-medium">分類／對方帳戶</th>
              <th class="lbl border-b border-line px-4 py-2.5 text-right font-medium">金額</th><th class="lbl border-b border-line px-4 py-2.5 text-right font-medium">交易後餘額</th>
            </tr></thead>
            <tbody>
              <tr v-for="r in ledger?.items ?? []" :key="r.transaction.id" class="cursor-pointer hover:bg-panel2" @click="ui.openTx({ transaction: r.transaction })">
                <td class="num border-b border-line px-4 py-3 text-muted">{{ r.transaction.date }}</td>
                <td class="border-b border-line px-4 py-3"><button type="button" class="text-left text-text" @click.stop="ui.openTx({ transaction: r.transaction })">{{ r.transaction.title }}</button></td>
                <td class="border-b border-line px-4 py-3 text-[13px] text-muted">{{ counterpart(r.transaction) }}</td>
                <td class="border-b border-line px-4 py-3 text-right"><Amount :value="r.amount" :currency="account.currency" /></td>
                <td class="num border-b border-line px-4 py-3 text-right">{{ formatAmount(r.balance_after, account.currency) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <EmptyState v-if="ledger && !ledger.items.length" title="這個帳戶還沒有交易" />
        <div v-if="ledger && ledger.total > SIZE" class="flex items-center justify-between px-5 py-3.5">
          <span class="num text-xs text-muted">共 {{ ledger.total }} 筆</span>
          <div class="flex gap-1.5">
            <AppButton size="sm" variant="ghost" :disabled="pageNo === 0" @click="pageNo--">上一頁</AppButton>
            <AppButton size="sm" variant="ghost" :disabled="(pageNo + 1) * SIZE >= ledger.total" @click="pageNo++">下一頁</AppButton>
          </div>
        </div>
      </AppCard>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, Plus } from 'lucide-vue-next'
import BalanceChart from '../components/BalanceChart.vue'
import Amount from '../components/ui/Amount.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import Badge from '../components/ui/Badge.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import IconButton from '../components/ui/IconButton.vue'
import SegmentedControl from '../components/ui/SegmentedControl.vue'
import StatCard from '../components/ui/StatCard.vue'
import ThemeToggle from '../components/ui/ThemeToggle.vue'
import api from '../services/api'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { useUiStore } from '../stores/ui'
import { thisMonth } from '../utils/date'
import { kindLabel } from '../utils/labels'
import { formatAmount } from '../utils/money'

// 帳戶詳細（SD 5.1）：餘額組成、每日餘額走勢、此帳戶交易與交易後餘額
const SIZE = 50
const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const accounts = useAccountsStore()
const categories = useCategoriesStore()
const id = computed(() => route.params.id)
const ledger = ref(null)
const history = ref([])
const days = ref('90')
const pageNo = ref(0)
const account = computed(() => ledger.value?.account)
const rangeOptions = [{ value: '30', label: '30 天' }, { value: '90', label: '90 天' }, { value: '365', label: '1 年' }]

// 本月流入／流出：從目前這一頁的資料算（一頁 50 筆，通常涵蓋本月；超過時以後端分頁為準）
const month = computed(() => {
  const m = thisMonth()
  const rows = (ledger.value?.items ?? []).filter(r => r.transaction.date.startsWith(m))
  const ins = rows.filter(r => Number(r.amount) > 0); const outs = rows.filter(r => Number(r.amount) < 0)
  return { in: ins.reduce((s, r) => s + Number(r.amount), 0), out: -outs.reduce((s, r) => s + Number(r.amount), 0), inCount: ins.length, outCount: outs.length }
})

function counterpart (t) {
  if (t.kind !== 'transfer') return categories.path(t.category_id)
  const other = t.entries.find(e => e.account_id !== id.value && e.role !== 'fee')
  const out = t.entries.find(e => e.role === 'transfer_out')?.account_id === id.value
  return `轉帳 ${out ? '→' : '←'} ${accounts.byId(other?.account_id)?.name ?? '?'}`
}

async function load () {
  const [l, h] = await Promise.all([
    api.get(`/accounts/${id.value}/ledger`, { params: { page: pageNo.value, size: SIZE } }),
    api.get(`/accounts/${id.value}/history`, { params: { days: days.value } }),
    accounts.ensure(), categories.ensure()
  ])
  ledger.value = l.data
  history.value = h.data
}

watch([id, pageNo, days], load, { immediate: true })
watch(() => ui.ledgerVersion, load)
</script>
