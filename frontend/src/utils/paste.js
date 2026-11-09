// 從 Excel／Google 試算表貼上（SA FR-3.14、SD 4.2.1）：解析 Tab 分隔文字、日期與金額格式、名稱比對
// 純函式，不碰 DOM，方便 node --test

/**
 * 剪貼簿文字 → 二維陣列。Excel 會把含換行或 Tab 的格子用雙引號包起來，引號內的 "" 代表一個 "。
 * 最後一行若是空的（Excel 複製會多一個換行）就去掉。
 */
export function parseClipboard (text) {
  const rows = []
  let row = []
  let cell = ''
  let quoted = false
  const s = String(text ?? '').replace(/\r\n?/g, '\n')
  for (let i = 0; i < s.length; i++) {
    const ch = s[i]
    if (quoted) {
      if (ch === '"' && s[i + 1] === '"') { cell += '"'; i++ } else if (ch === '"') quoted = false
      else cell += ch
    } else if (ch === '"' && cell === '') quoted = true
    else if (ch === '\t') { row.push(cell); cell = '' } else if (ch === '\n') { row.push(cell); rows.push(row); row = []; cell = '' } else cell += ch
  }
  row.push(cell)
  rows.push(row)
  while (rows.length && rows.at(-1).every(c => c.trim() === '')) rows.pop()
  return rows.map(r => r.map(c => c.trim()))
}

const pad = (n) => String(n).padStart(2, '0')
const valid = (y, m, d) => {
  const dt = new Date(Date.UTC(y, m - 1, d))
  return dt.getUTCFullYear() === y && dt.getUTCMonth() === m - 1 && dt.getUTCDate() === d
}

/**
 * 日期 → YYYY-MM-DD；無法辨識回傳 null。
 * 接受 2026-11-09、2026/11/9、2026.11.9、2026年11月9日、11/9（今年）、民國 115/11/09（三位數年份 +1911）
 */
export function parseDate (raw, today) {
  const s = String(raw ?? '').trim().replace(/[年月]/g, '/').replace(/日$/, '').replace(/^民國\s*/, '')
  let m = s.match(/^(\d{2,4})[-/.](\d{1,2})[-/.](\d{1,2})$/)
  let y, mo, d
  if (m) {
    y = Number(m[1]); mo = Number(m[2]); d = Number(m[3])
    if (m[1].length <= 3) y += 1911
  } else if ((m = s.match(/^(\d{1,2})[-/.](\d{1,2})$/))) {
    y = Number(today.slice(0, 4)); mo = Number(m[1]); d = Number(m[2])
  } else return null
  return valid(y, mo, d) ? `${y}-${pad(mo)}-${pad(d)}` : null
}

/**
 * 金額 → 數字字串（保留正負號）；空白回傳 ''，無法辨識回傳 null。
 * 去掉千分位、幣別符號與「元」；(120) 或 120- 視為負數。也保留算式（120+35）交給金額欄位計算
 */
export function parseAmount (raw) {
  let s = String(raw ?? '').trim()
  if (!s) return ''
  let neg = false
  if (/^\(.*\)$/.test(s)) { neg = true; s = s.slice(1, -1) }
  if (/-$/.test(s)) { neg = true; s = s.slice(0, -1) }
  s = s.replace(/NT\$|US\$|TWD|USD|JPY|[$¥元,，\s]/gi, '').replace(/−/g, '-')
  if (/^[-+]?\d*\.?\d+$/.test(s)) {
    const v = s.replace(/^\+/, '')
    return neg && !v.startsWith('-') ? '-' + v : v
  }
  if (/^[-+*/().\d]+$/.test(s) && /\d/.test(s)) return s
  return null
}

const KINDS = { 支出: 'expense', 支: 'expense', expense: 'expense', 收入: 'income', 收: 'income', income: 'income', 轉帳: 'transfer', 轉: 'transfer', transfer: 'transfer' }
/** 類型文字 → expense / income / transfer；空白或無法辨識回傳 null */
export function parseKind (raw) {
  return KINDS[String(raw ?? '').trim().toLowerCase()] ?? null
}

const norm = (s) => String(s ?? '').replace(/\s+/g, '').toLowerCase()

/**
 * 名稱比對（SD 4.2.1）：完全相同 → 去除空白後相同 → 包含（任一方向），依序比對；找不到回傳 null。
 * items：[{ id, names: [可比對的名稱...] }]；包含比對有多個符合時取名稱最短的（最接近）
 */
export function matchName (items, raw) {
  const q = String(raw ?? '').trim()
  if (!q) return null
  const nq = norm(q)
  for (const test of [(n) => n === q, (n) => norm(n) === nq]) {
    const hit = items.find(it => it.names.some(test))
    if (hit) return hit.id
  }
  let best = null
  let bestLen = Infinity
  for (const it of items) {
    for (const n of it.names) {
      const nn = norm(n)
      if (nn && (nn.includes(nq) || nq.includes(nn)) && nn.length < bestLen) { best = it.id; bestLen = nn.length }
    }
  }
  return best
}
