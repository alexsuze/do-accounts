// 金額顯示（SD 4.3）：幣別小數位數、千分位、正負號。API 傳來的金額是字串，這裡只做顯示，不做加總
// 後端 Currencies 與此表內容須一致
export const CURRENCIES = {
  TWD: { digits: 0, symbol: 'NT$' },
  JPY: { digits: 0, symbol: '¥' },
  USD: { digits: 2, symbol: 'US$' }
}

const MINUS = '−' // U+2212，比連字號寬，與 + 對齊

export function digitsOf (currency) {
  return CURRENCIES[currency]?.digits ?? 2
}

/**
 * 依幣別四捨五入並加千分位。
 * @param {string|number} value 例如 "-1234.5"
 * @param {object} opt sign：'auto'（只有負數加 −）/ 'always'（正數也加 +）/ 'none'；symbol：是否加幣別符號
 */
export function formatAmount (value, currency = 'TWD', { sign = 'auto', symbol = false } = {}) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  const digits = digitsOf(currency)
  const abs = Math.abs(n).toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits })
  const isZero = Number(abs.replace(/,/g, '')) === 0
  const prefix = sign === 'none' || isZero ? '' : n < 0 ? MINUS : sign === 'always' ? '+' : ''
  const sym = symbol ? (CURRENCIES[currency]?.symbol ?? currency) + ' ' : ''
  return prefix + sym + abs
}

/** 正 / 負 / 零，給顏色判斷用 */
export function signOf (value) {
  const n = Number(value)
  if (!Number.isFinite(n) || n === 0) return 0
  return n > 0 ? 1 : -1
}
