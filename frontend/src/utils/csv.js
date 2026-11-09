// CSV 讀取（SA FR-10.2、SD 4.7、ADR-17）：在瀏覽器解碼與切欄位，純函式方便 node --test

/**
 * 位元組 → 文字。先試 UTF-8（有錯就丟例外），失敗改用 Big5（台灣銀行對帳單常見）；去掉開頭的 BOM。
 * @returns {{ text: string, encoding: 'UTF-8' | 'Big5' }}
 */
export function decodeBytes (bytes) {
  let text
  let encoding = 'UTF-8'
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    text = new TextDecoder('big5').decode(bytes)
    encoding = 'Big5'
  }
  return { text: text.replace(/^﻿/, ''), encoding }
}

/** 由第一行（引號外）出現最多的符號判斷分隔符號：逗號、Tab 或分號 */
export function detectDelimiter (text) {
  const counts = { ',': 0, '\t': 0, ';': 0 }
  let quoted = false
  for (const ch of String(text ?? '')) {
    if (ch === '"') quoted = !quoted
    else if (!quoted && ch === '\n') break
    else if (!quoted && ch in counts) counts[ch]++
  }
  const [best, n] = Object.entries(counts).sort((a, b) => b[1] - a[1])[0]
  return n ? best : ','
}

/**
 * 文字 → 二維陣列。雙引號包住的格子可含分隔符號與換行，"" 代表一個 "；去掉每格前後空白與最後的空行。
 */
export function parseDelimited (text, delimiter = ',') {
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
    } else if (ch === '"' && cell.trim() === '') { quoted = true; cell = '' } else if (ch === delimiter) { row.push(cell); cell = '' } else if (ch === '\n') { row.push(cell); rows.push(row); row = []; cell = '' } else cell += ch
  }
  row.push(cell)
  rows.push(row)
  while (rows.length && rows.at(-1).every(c => c.trim() === '')) rows.pop()
  return rows.map(r => r.map(c => c.trim()))
}
