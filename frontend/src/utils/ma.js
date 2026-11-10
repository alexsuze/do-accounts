// 移動平均（K 線圖的 5／20／60 日均線）：純函式，方便 node --test

/**
 * 簡單移動平均。資料不足 n 天的位置回傳 null（圖上不畫）
 * @param values 收盤價（舊到新）
 */
export function movingAverage (values, n) {
  const out = []
  let sum = 0
  for (let i = 0; i < values.length; i++) {
    sum += values[i]
    if (i >= n) sum -= values[i - n]
    out.push(i >= n - 1 ? sum / n : null)
  }
  return out
}

/** 期間選項（月數） */
export const CHART_RANGES = [
  { value: '3', label: '3M' }, { value: '6', label: '6M' }, { value: '12', label: '1Y' }, { value: '24', label: '2Y' }
]
