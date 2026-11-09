<template>
  <div class="flex flex-col gap-6">
    <PageHeader eyebrow="Dashboard" title="總覽">
      <span class="num hidden rounded-[10px] border border-line bg-panel px-3.5 py-2.5 text-[13px] text-muted md:inline">{{ now }}</span>
      <AppButton @click="ui.openTx()"><Plus :size="16" aria-hidden="true" />記帳</AppButton>
    </PageHeader>

    <template v-if="d">
      <section class="grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-4">
        <StatCard glow label="淨資產 · Net worth" :value="fmt(d.net_worth, { symbol: true })" :hint="foreignHint" />
        <StatCard label="總資產 · Assets" :value="fmt(d.assets)" :hint="`${d.base_currency}`" />
        <StatCard label="總負債 · Liabilities" :value="fmt(d.liabilities)" :tone="Number(d.liabilities) < 0 ? 'exp' : ''" hint="信用卡、貸款與負餘額帳戶" />
      </section>

      <section class="flex flex-wrap gap-4">
        <AppCard class="flex min-w-0 flex-[1_1_320px] flex-col gap-4">
          <div class="flex items-center"><h2 class="m-0 mr-auto font-head text-base font-semibold">本月收支 · {{ Number(d.month.slice(5)) }} 月</h2>
            <RouterLink :to="{ path: '/transactions', query: { month: d.month } }" class="text-[13px]">明細 →</RouterLink></div>
          <div class="flex gap-3">
            <div class="flex flex-1 flex-col gap-1 rounded-[10px] bg-panel2 p-3"><span class="lbl">收入</span><Amount :value="d.month_totals.income" :currency="d.base_currency" class="text-xl" /></div>
            <div class="flex flex-1 flex-col gap-1 rounded-[10px] bg-panel2 p-3"><span class="lbl">支出</span><Amount :value="-d.month_totals.expense" :currency="d.base_currency" class="text-xl" /></div>
          </div>
          <div class="flex justify-between text-sm"><span class="text-muted">結餘</span><Amount :value="d.month_totals.income - d.month_totals.expense" :currency="d.base_currency" /></div>
          <p v-for="(t, cur) in d.month_other_currencies" :key="cur" class="num m-0 text-xs text-muted">
            另有 {{ cur }}：收入 {{ fmt(t.income, {}, cur) }}、支出 {{ fmt(t.expense, {}, cur) }}（階段 2 起依匯率換算併入）
          </p>
          <p class="m-0 text-xs text-muted">預算進度與淨資產趨勢會在階段 2 加入。</p>
        </AppCard>

        <AppCard :padded="false" class="flex min-w-0 flex-[2_1_480px] flex-col">
          <div class="flex items-center px-5 pt-5 pb-2"><h2 class="m-0 mr-auto font-head text-base font-semibold">最近交易</h2><RouterLink to="/transactions" class="text-[13px]">全部明細 →</RouterLink></div>
          <TransactionRow v-for="t in d.recent" :key="t.id" :tx="t" show-date @open="ui.openTx({ transaction: t })" />
          <EmptyState v-if="!d.recent.length" title="還沒有任何交易" text="先在「帳戶」頁建立帳戶，再按「記帳」開始。">
            <AppButton v-if="!accounts.active.length" variant="ghost" to="/accounts">建立帳戶</AppButton>
            <AppButton v-else @click="ui.openTx()"><Plus :size="16" aria-hidden="true" />記第一筆</AppButton>
          </EmptyState>
        </AppCard>
      </section>

      <AppCard class="flex flex-col gap-3">
        <div class="flex items-center"><h2 class="m-0 mr-auto font-head text-base font-semibold">帳戶</h2><RouterLink to="/accounts" class="text-[13px]">管理帳戶 →</RouterLink></div>
        <EmptyState v-if="!accounts.groups.length" title="還沒有帳戶" text="建立錢包、銀行、信用卡等帳戶後，這裡會依類型顯示餘額。">
          <AppButton to="/accounts"><Plus :size="16" aria-hidden="true" />建立帳戶</AppButton>
        </EmptyState>
        <div class="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-x-6 gap-y-4">
          <div v-for="g in visibleGroups" :key="g.value" class="flex flex-col gap-1">
            <div class="flex justify-between py-1"><span class="lbl">{{ g.label }} · {{ g.en }}</span></div>
            <RouterLink v-for="a in g.items" :key="a.id" :to="`/accounts/${a.id}`"
              class="flex min-h-11 items-center gap-2.5 rounded-[10px] bg-panel2 px-3 text-text no-underline hover:brightness-110">
              <span class="size-1.5 rounded-full bg-accent" aria-hidden="true" />
              <span class="mr-auto truncate">{{ a.name }}</span>
              <span v-if="a.currency !== d.base_currency" class="num text-xs text-muted">{{ a.currency }}</span>
              <Amount :value="a.balance" :currency="a.currency" sign="auto" :plain="Number(a.balance) >= 0" class="text-sm font-medium" />
            </RouterLink>
          </div>
        </div>
      </AppCard>
    </template>
    <AppCard v-else-if="!error" class="py-12 text-center text-muted">載入中…</AppCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
import Amount from '../components/ui/Amount.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import StatCard from '../components/ui/StatCard.vue'
import TransactionRow from '../components/TransactionRow.vue'
import api from '../services/api'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { useUiStore } from '../stores/ui'
import { useClock } from '../composables/useClock'
import { formatAmount } from '../utils/money'

// 總覽（SA FR-7）。階段 1 只計本位幣；外幣帳戶另列（階段 2 加匯率）
const ui = useUiStore()
const accounts = useAccountsStore()
const categories = useCategoriesStore()
const now = useClock()
const d = ref(null)
const error = ref(false)

const fmt = (v, opt = {}, cur) => formatAmount(v, cur ?? d.value?.base_currency ?? 'TWD', { sign: 'auto', ...opt })
const visibleGroups = computed(() => accounts.groups.map(g => ({ ...g, items: g.items.filter(a => a.visible) })).filter(g => g.items.length))
const foreignHint = computed(() => {
  const f = d.value?.foreign_accounts ?? []
  return f.length ? `未含外幣帳戶 ${f.length} 個（${f.map(a => a.currency).filter((c, i, arr) => arr.indexOf(c) === i).join('、')}），階段 2 加入匯率換算` : `以 ${d.value?.base_currency} 計`
})

async function load () {
  try {
    const [res] = await Promise.all([api.get('/reports/dashboard'), accounts.load(), categories.ensure()])
    d.value = res.data
    error.value = false
  } catch {
    error.value = true
  }
}
onMounted(load)
watch(() => ui.ledgerVersion, load)
</script>
