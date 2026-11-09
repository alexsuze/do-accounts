// 日期工具：交易日期一律是 YYYY-MM-DD 字串，「今天」以台北時間為準（SD 2.1-4）
const TZ = 'Asia/Taipei'
const WEEK = ['日', '一', '二', '三', '四', '五', '六']

/** 台北時間的今天，YYYY-MM-DD */
export function today (now = new Date()) {
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-CA', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(now).map(x => [x.type, x.value]))
  return `${p.year}-${p.month}-${p.day}`
}

/** YYYY-MM */
export function thisMonth (now = new Date()) {
  return today(now).slice(0, 7)
}

/** 月份加減，例如 shiftMonth('2026-01', -1) === '2025-12' */
export function shiftMonth (ym, n) {
  const [y, m] = ym.split('-').map(Number)
  const t = y * 12 + (m - 1) + n
  return `${Math.floor(t / 12)}-${String(t % 12 + 1).padStart(2, '0')}`
}

/** 某月第一天與最後一天 */
export function monthRange (ym) {
  const [y, m] = ym.split('-').map(Number)
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate()
  return { from: `${ym}-01`, to: `${ym}-${String(last).padStart(2, '0')}` }
}

/** 星期幾（日～六） */
export function weekday (date) {
  const [y, m, d] = date.split('-').map(Number)
  return WEEK[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]
}

/** 11/09 */
export function shortDate (date) {
  return date ? `${date.slice(5, 7)}/${date.slice(8, 10)}` : ''
}

export function isValidDate (s) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s ?? '')) return false
  const [y, m, d] = s.split('-').map(Number)
  const dt = new Date(Date.UTC(y, m - 1, d))
  return dt.getUTCFullYear() === y && dt.getUTCMonth() === m - 1 && dt.getUTCDate() === d
}
