import { onBeforeUnmount, onMounted, ref } from 'vue'

const fmt = new Intl.DateTimeFormat('zh-TW', {
  timeZone: 'Asia/Taipei', year: 'numeric', month: '2-digit', day: '2-digit',
  weekday: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
})

/** 每秒更新的台北時間字串，例如 2026/11/09（一）14:32:08（SA FR-11.2） */
export function useClock () {
  const now = ref(format(new Date()))
  let timer
  onMounted(() => { timer = setInterval(() => { now.value = format(new Date()) }, 1000) })
  onBeforeUnmount(() => clearInterval(timer))
  return now
}

function format (d) {
  const p = Object.fromEntries(fmt.formatToParts(d).map(x => [x.type, x.value]))
  return `${p.year}/${p.month}/${p.day}（${p.weekday.replace('週', '')}）${p.hour}:${p.minute}:${p.second}`
}
