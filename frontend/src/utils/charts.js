// 圖表計算（純函式，SD 5.5 報表元件共用）。圖表本身用 SVG 繪製，顏色全部取 CSS 變數，深淺色自動跟著切換

/** 不小於 v 的「好看」上限：1、2、2.5、5 × 10ⁿ */
export function niceMax (v) {
  if (!(v > 0)) return 1
  const p = 10 ** Math.floor(Math.log10(v))
  for (const k of [1, 2, 2.5, 5, 10]) if (k * p >= v) return k * p
  return 10 * p
}

/**
 * 每日長條圖的高度上限：以支出為準；最大的一天比第二大的多 3 倍以上（例如房租）時，
 * 改用第二大的那天，避免其他天都扁到看不見（超出的部分在圖上另外標示金額）
 */
export function dailyCap (expenses) {
  const sorted = expenses.filter(v => v > 0).sort((a, b) => b - a)
  if (!sorted.length) return 1
  const ref = sorted.length > 1 && sorted[0] > sorted[1] * 3 ? sorted[1] * 1.2 : sorted[0] * 1.05
  return niceMax(ref)
}

/** 線性比例尺：把 [d0, d1] 對應到 [r0, r1] */
export function scale (d0, d1, r0, r1) {
  const span = d1 - d0 || 1
  return (v) => r0 + (v - d0) / span * (r1 - r0)
}

/**
 * 環形圖每一段的 stroke-dasharray 與 stroke-dashoffset。
 * @param values 各段數值（≥ 0）
 * @param circumference 圓周長
 */
export function donutSegments (values, circumference) {
  const total = values.reduce((s, v) => s + Math.max(0, v), 0)
  let acc = 0
  return values.map(v => {
    const len = total ? Math.max(0, v) / total * circumference : 0
    const seg = { dash: `${len.toFixed(2)} ${circumference.toFixed(2)}`, offset: (-acc).toFixed(2), length: len }
    acc += len
    return seg
  })
}

/** 預算顏色：超過 100% 紅、達門檻橘、其餘綠（SA FR-5.5） */
export function budgetTone (pct, warnPct = 80) {
  const p = Number(pct)
  if (p > 100) return 'exp'
  if (p >= warnPct) return 'warn'
  return 'inc'
}

/** 簡短金額：68,500 → 68.5k；1,250,000 → 1.25M */
export function compact (n) {
  const v = Math.abs(Number(n))
  const sign = Number(n) < 0 ? '−' : ''
  if (v >= 1e6) return sign + trim(v / 1e6) + 'M'
  if (v >= 1e3) return sign + trim(v / 1e3) + 'k'
  return sign + Math.round(v).toString()
}

function trim (x) {
  return x.toFixed(x >= 100 ? 0 : x >= 10 ? 1 : 2).replace(/\.?0+$/, '')
}

/** 折線路徑：points 為 [x, y] */
export function linePath (points) {
  return points.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join('')
}
