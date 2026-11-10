<template>
  <div ref="el" class="h-full w-full" role="img" :aria-label="label" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { CandlestickSeries, HistogramSeries, LineSeries, LineStyle, createChart, createSeriesMarkers } from 'lightweight-charts'
import { movingAverage } from '../../utils/ma'
import { useUiStore } from '../../stores/ui'

// K 線圖（SD 4.10，TradingView lightweight-charts）：日 K＋成交量＋5／20／60 日均線＋均價線＋買賣標記。
// 顏色取 CSS 變數，切換深淺色時重畫；台股慣例紅漲綠跌（--up／--down）
const props = defineProps({
  /** kind = candle：[{ date, open, high, low, close, volume }]；kind = line：[{ date, close }] */
  bars: { type: Array, default: () => [] },
  closes: { type: Array, default: () => [] },
  kind: { type: String, default: 'candle' },
  /** 只顯示這一天以後（之前的資料只用來算均線） */
  from: { type: String, default: '' },
  avgCost: { type: [String, Number], default: null },
  markers: { type: Array, default: () => [] },
  label: { type: String, default: 'K 線圖' }
})
const emit = defineEmits(['legend'])
const el = ref(null)
const ui = useUiStore()
let chart = null

const MAS = [{ n: 5, color: '--c4' }, { n: 20, color: '--c2' }, { n: 60, color: '--c3' }]
const TRADE = { buy: ['買', 'belowBar', 'arrowUp'], sell: ['賣', 'aboveBar', 'arrowDown'], cash_dividend: ['息', 'aboveBar', 'circle'], stock_dividend: ['配', 'aboveBar', 'circle'], opening: ['初', 'belowBar', 'square'] }

function css (name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function draw () {
  chart?.remove()
  chart = null
  if (!el.value) return
  const c = {
    text: css('--muted'), line: css('--line'), bg: css('--panel'), up: css('--up'), down: css('--down'), accent: css('--accent')
  }
  chart = createChart(el.value, {
    autoSize: true,
    layout: { background: { color: c.bg }, textColor: c.text, fontFamily: 'JetBrains Mono, ui-monospace, monospace', attributionLogo: false },
    grid: { vertLines: { color: c.line }, horzLines: { color: c.line } },
    rightPriceScale: { borderColor: c.line },
    timeScale: { borderColor: c.line },
    localization: { locale: 'zh-TW', dateFormat: 'yyyy/MM/dd' }
  })

  const all = props.kind === 'candle' ? props.bars : props.closes
  const closes = all.map(b => Number(b.close))
  const visible = (d) => !props.from || d >= props.from
  let main
  if (props.kind === 'candle') {
    main = chart.addSeries(CandlestickSeries, {
      upColor: c.up, downColor: c.down, borderUpColor: c.up, borderDownColor: c.down, wickUpColor: c.up, wickDownColor: c.down
    })
    main.setData(props.bars.filter(b => visible(b.date)).map(b => ({ time: b.date, open: Number(b.open), high: Number(b.high), low: Number(b.low), close: Number(b.close) })))
    const vol = chart.addSeries(HistogramSeries, { priceScaleId: 'vol', priceFormat: { type: 'volume' }, lastValueVisible: false, priceLineVisible: false })
    chart.priceScale('vol').applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } })
    vol.setData(props.bars.filter(b => visible(b.date)).map(b => ({
      time: b.date, value: Number(b.volume), color: (Number(b.close) >= Number(b.open) ? c.up : c.down) + '66'
    })))
    main.priceScale().applyOptions({ scaleMargins: { top: 0.06, bottom: 0.26 } })
  } else {
    main = chart.addSeries(LineSeries, { color: c.accent, lineWidth: 2 })
    main.setData(props.closes.filter(b => visible(b.date)).map(b => ({ time: b.date, value: Number(b.close) })))
  }

  // 均線：用含期間之前的資料計算，只畫期間內
  const legend = {}
  if (props.kind === 'candle') {
    for (const m of MAS) {
      const ma = movingAverage(closes, m.n)
      const s = chart.addSeries(LineSeries, { color: css(m.color), lineWidth: 1, priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false })
      s.setData(all.map((b, i) => ({ time: b.date, value: ma[i] })).filter(p => p.value != null && visible(p.time)))
      legend[`ma${m.n}`] = ma.at(-1)
    }
  }
  if (props.avgCost != null) {
    main.createPriceLine({ price: Number(props.avgCost), color: c.accent, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: '均價' })
  }
  const dates = new Set(all.filter(b => visible(b.date)).map(b => b.date))
  const marks = props.markers
    .filter(m => TRADE[m.trade_type] && dates.has(m.date))
    .sort((a, b) => a.date.localeCompare(b.date))
    .map(m => {
      const [text, position, shape] = TRADE[m.trade_type]
      return { time: m.date, position, shape, text, color: m.trade_type === 'sell' ? c.down : m.trade_type === 'buy' ? c.up : c.accent }
    })
  if (marks.length) createSeriesMarkers(main, marks)
  chart.timeScale().fitContent()
  emit('legend', { ...legend, close: closes.at(-1), date: all.at(-1)?.date })
}

onMounted(draw)
watch(() => [props.bars, props.closes, props.avgCost, props.markers, props.from, props.kind], draw)
watch(() => ui.dark, () => requestAnimationFrame(draw))
onBeforeUnmount(() => chart?.remove())
</script>
