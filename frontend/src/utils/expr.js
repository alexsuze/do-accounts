// 金額欄位的算式（SA FR-3.6、SD 4.3）：只接受數字、+ - * / ( ) . 與空白，不使用 eval。
// 用 decimal.js-light 計算，避免 0.1 + 0.2 這類浮點誤差。
import Decimal from 'decimal.js-light'

const TOKEN = /\s*(\d+(?:\.\d*)?|\.\d+|[-+*/()×÷−])\s*/y

function tokenize (src) {
  const out = []
  TOKEN.lastIndex = 0
  const s = src.replace(/,/g, '')
  while (TOKEN.lastIndex < s.length) {
    const start = TOKEN.lastIndex
    const m = TOKEN.exec(s)
    if (!m) throw new Error(`無法辨識的字元：「${s.slice(start, start + 1)}」`)
    out.push(m[1].replace('×', '*').replace('÷', '/').replace('−', '-'))
  }
  return out
}

/**
 * 計算算式，回傳 Decimal。空字串回傳 null。
 * 文法：expr = term (('+'|'-') term)*；term = factor (('*'|'/') factor)*；factor = ('-'|'+') factor | number | '(' expr ')'
 */
export function evaluate (src) {
  if (src == null || String(src).trim() === '') return null
  const tokens = tokenize(String(src))
  let i = 0
  const peek = () => tokens[i]
  const next = () => tokens[i++]

  function expr () {
    let v = term()
    while (peek() === '+' || peek() === '-') v = next() === '+' ? v.plus(term()) : v.minus(term())
    return v
  }
  function term () {
    let v = factor()
    while (peek() === '*' || peek() === '/') {
      const op = next()
      const r = factor()
      if (op === '/' && r.isZero()) throw new Error('不能除以 0')
      v = op === '*' ? v.times(r) : v.div(r)
    }
    return v
  }
  function factor () {
    const t = next()
    if (t === undefined) throw new Error('算式不完整')
    if (t === '-') return factor().negated()
    if (t === '+') return factor()
    if (t === '(') {
      const v = expr()
      if (next() !== ')') throw new Error('括號沒有成對')
      return v
    }
    if (/^[\d.]/.test(t)) return new Decimal(t)
    throw new Error(`算式在「${t}」附近有誤`)
  }

  const v = expr()
  if (i < tokens.length) throw new Error(`算式在「${tokens[i]}」附近有誤`)
  return v
}

/** 是否包含運算子（只有數字時不必顯示「= 結果」） */
export function isExpression (src) {
  return /[-+*/×÷−()]/.test(String(src ?? '').replace(/^\s*-/, ''))
}

/**
 * 依幣別位數四捨五入成字串（送給 API 的格式，不含千分位）。
 * 失敗時丟出帶中文訊息的 Error。
 */
export function toAmountString (src, digits) {
  const v = evaluate(src)
  if (v === null) return null
  return v.toDecimalPlaces(digits, Decimal.ROUND_HALF_UP).toFixed(digits)
}
