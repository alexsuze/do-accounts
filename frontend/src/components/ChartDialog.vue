<template>
  <AppDialog :open="open" :title="title" :width="980" @update:open="v => emit('update:open', v)">
    <div class="flex flex-col gap-3">
      <div class="flex flex-wrap items-center gap-3">
        <SegmentedControl v-model="months" :options="CHART_RANGES" label="期間" />
        <div v-if="data" class="num mr-auto flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
          <template v-if="data.kind === 'candle'">
            <span class="flex items-center gap-1.5"><span class="h-0.5 w-3.5 bg-[var(--c4)]" />MA5 {{ fmtPx(legend.ma5) }}</span>
            <span class="flex items-center gap-1.5"><span class="h-0.5 w-3.5 bg-[var(--c2)]" />MA20 {{ fmtPx(legend.ma20) }}</span>
            <span class="flex items-center gap-1.5"><span class="h-0.5 w-3.5 bg-[var(--c3)]" />MA60 {{ fmtPx(legend.ma60) }}</span>
          </template>
          <span v-if="data.avg_cost" class="flex items-center gap-1.5 text-accent"><span class="w-3.5 border-t border-dashed border-accent" />均價 {{ fmtPx(data.avg_cost) }}（{{ qty(data.quantity) }} 股）</span>
          <span v-if="legend.date" class="text-muted">{{ legend.date }} 收盤 {{ fmtPx(legend.close) }}</span>
        </div>
      </div>
      <div class="relative h-[min(60vh,460px)] overflow-hidden rounded-[12px] border border-line">
        <CandleChart v-if="data && points.length" :kind="data.kind" :bars="data.bars" :closes="data.closes" :from="data.from"
          :avg-cost="data.avg_cost" :markers="data.markers" :label="ariaLabel" @legend="v => legend = v" />
        <div v-if="loading" class="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-panel/80 text-sm text-muted">
          <span class="size-6 animate-spin rounded-full border-2 border-accent border-t-transparent" aria-hidden="true" />
          <span>載入中…第一次開啟會向{{ security?.market === 'TWO' ? '櫃買中心' : '證交所' }}補抓資料，可能需要數十秒</span>
        </div>
        <p v-else-if="data && !points.length" class="m-0 flex h-full items-center justify-center px-6 text-center text-sm text-muted">
          {{ data.kind === 'line' ? '這檔證券沒有自動行情來源，也還沒有輸入過報價。可以在持倉表點「現價」手動輸入。' : '這段期間沒有行情資料。' }}
        </p>
      </div>
      <p v-if="data && !data.complete" class="m-0 text-xs text-warn">交易所暫時連不到，部分月份的資料還沒抓到，稍後再開一次會自動補齊。</p>
      <p v-if="data?.kind === 'line'" class="m-0 text-xs text-muted">這檔證券沒有自動行情來源，只顯示收盤價折線（排程與手動輸入的報價）。</p>
      <p v-else class="m-0 text-xs text-muted">日 K 線，紅漲綠跌；箭頭為你的買進（買）、賣出（賣）與配息（息）。</p>
    </div>
  </AppDialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import AppDialog from './ui/AppDialog.vue'
import SegmentedControl from './ui/SegmentedControl.vue'
import CandleChart from './charts/CandleChart.vue'
import api from '../services/api'
import { CHART_RANGES } from '../utils/ma'

// K 線圖視窗（投資頁持倉表開啟）。預設 6 個月，可切換 3M／6M／1Y／2Y
const props = defineProps({
  open: Boolean,
  security: { type: Object, default: null },
  /** 指定帳戶時，均價線與買賣標記只看這個帳戶 */
  accountId: { type: String, default: null }
})
const emit = defineEmits(['update:open'])
const months = ref('6')
const data = ref(null)
const loading = ref(false)
const legend = ref({})

const title = computed(() => props.security ? `${props.security.symbol} ${props.security.name} · K 線圖` : 'K 線圖')
const points = computed(() => data.value ? (data.value.kind === 'candle' ? data.value.bars : data.value.closes) : [])
const ariaLabel = computed(() => `${props.security?.symbol} 近 ${months.value} 個月${data.value?.kind === 'candle' ? '日 K 線' : '收盤價'}`)
const fmtPx = (v) => v == null ? '—' : Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const qty = (v) => Number(v).toLocaleString('en-US', { maximumFractionDigits: 6 })

async function load () {
  if (!props.open || !props.security) return
  const id = props.security.id
  const m = months.value
  loading.value = true
  try {
    const { data: d } = await api.get(`/investments/securities/${id}/chart`, {
      params: { months: m, ...(props.accountId ? { account_id: props.accountId } : {}) }, timeout: 120000
    })
    if (id === props.security?.id && m === months.value) data.value = d
  } finally {
    loading.value = false
  }
}

watch(() => [props.open, props.security?.id], ([o]) => {
  if (!o) return
  months.value = '6'
  data.value = null
  legend.value = {}
  load()
})
watch(months, load)
</script>
