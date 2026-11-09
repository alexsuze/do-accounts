// CSV 匯入的欄位對應（SA FR-10.2～10.4、SD 4.7、ADR-17）：把 CSV 表格依對應規則整理成後端的 ImportRow
// 純函式，方便 node --test
import { parseAmount, parseDate, parseKind } from './paste.js'

/** 可以對應的系統欄位 */
export const FIELDS = [
  { value: 'ignore', label: '（不匯入）' },
  { value: 'date', label: '日期' },
  { value: 'kind', label: '類型（支出／收入／轉帳）' },
  { value: 'title', label: '項目' },
  { value: 'category', label: '分類（大類或完整名稱）' },
  { value: 'subcategory', label: '子類' },
  { value: 'account', label: '帳戶' },
  { value: 'amount', label: '金額' },
  { value: 'income', label: '收入金額' },
  { value: 'expense', label: '支出金額' },
  { value: 'to_account', label: '轉入帳戶' },
  { value: 'to_amount', label: '轉入金額' },
  { value: 'fee', label: '手續費' },
  { value: 'tags', label: '標籤' },
  { value: 'note', label: '備註' }
]

/** 金額正負規則 */
export const AMOUNT_RULES = [
  { value: 'signed', label: '單一欄，負數為支出' },
  { value: 'split', label: '收入欄、支出欄分開' },
  { value: 'positive_expense', label: '單一欄，正數為支出（信用卡帳單）' }
]

export const DATE_ORDERS = [
  { value: 'ymd', label: '年/月/日（含民國年）' },
  { value: 'mdy', label: '月/日/年' },
  { value: 'dmy', label: '日/月/年' }
]

// 標題關鍵字 → 欄位（依序比對，先比對到的優先）
const GUESS = [
  ['to_amount', /轉入金額/],
  ['to_account', /轉入帳戶/],
  ['subcategory', /子類|子分類/],
  ['fee', /手續費|fee/i],
  ['date', /日期|date|交易日|入帳日/i],
  ['kind', /^類型$|^收支$|^type$/i],
  ['income', /存入|收入金額|^收入$|credit|deposit/i],
  ['expense', /支出金額|提出|支取|^支出$|debit|withdraw/i],
  ['amount', /金額|amount/i],
  ['category', /大類|分類|類別|category/i],
  ['account', /帳戶|account/i],
  ['tags', /標籤|tag/i],
  ['note', /備註|memo|note/i],
  ['title', /項目|摘要|說明|明細|名稱|商店|description|title/i]
]

/** 依標題猜欄位對應；同一個欄位只對應一次，其餘為 ignore */
export function guessColumns (headers) {
  const used = new Set()
  return headers.map(h => {
    const hit = GUESS.find(([f, re]) => !used.has(f) && re.test(String(h ?? '').trim()))
    if (!hit) return 'ignore'
    used.add(hit[0])
    return hit[0]
  })
}

/** 範本 mapping → 這個檔案的欄位對應（依標題名稱）；範本沒提到的欄位為 ignore */
export function columnsFromTemplate (headers, mapping) {
  const by = mapping?.by_header ?? {}
  return headers.map(h => by[h] ?? 'ignore')
}

/** 目前的欄位對應 → 範本 mapping（只存有對應的欄位） */
export function templateFromColumns (headers, columns, opts) {
  const by = {}
  headers.forEach((h, i) => { if (columns[i] && columns[i] !== 'ignore') by[h] = columns[i] })
  return { by_header: by, amount_rule: opts.amountRule, date_order: opts.dateOrder, default_account_id: opts.defaultAccountId ?? null }
}

/** 內建範本「do-accounts 格式」：本系統匯出的 CSV 可以直接匯入（SD 4.7） */
export const BUILTIN_TEMPLATE = {
  id: 'builtin',
  name: 'do-accounts 格式（內建）',
  mapping: {
    by_header: {
      日期: 'date', 類型: 'kind', 項目: 'title', 大類: 'category', 子類: 'subcategory', 帳戶: 'account', 金額: 'amount',
      轉入帳戶: 'to_account', 轉入金額: 'to_amount', 手續費: 'fee', 標籤: 'tags', 備註: 'note'
    },
    amount_rule: 'signed',
    date_order: 'ymd',
    default_account_id: null
  }
}

/** 本系統匯出時為了防 CSV injection 加的 ' 前綴，匯入時拿掉 */
export function unguard (s) {
  return /^'[=+\-@]/.test(s) ? s.slice(1) : s
}

function toYmd (raw, order, today) {
  const s = String(raw ?? '').trim()
  if (order !== 'ymd') {
    const m = s.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$/)
    if (m) {
      const [mo, d] = order === 'mdy' ? [m[1], m[2]] : [m[2], m[1]]
      return parseDate(`${m[3]}/${mo}/${d}`, today)
    }
  }
  return parseDate(s, today)
}

const num = (raw) => {
  const v = parseAmount(raw)
  if (v === '' || v === null) return v
  const n = Number(v)
  return Number.isFinite(n) ? v : null // 算式不接受
}
const abs = (v) => v.replace(/^-/, '')

/**
 * CSV 資料列（不含標題列）→ 後端的 ImportRow。無法解析的日期、金額照原樣（或 null）送出，由後端標示錯誤，
 * 讓錯誤訊息只有一個來源。
 * @param body 資料列
 * @param columns 每一欄對應的欄位（FIELDS 的 value）
 * @param opts { amountRule, dateOrder, today, firstLine }：firstLine 為第一筆資料在檔案中的列號（有標題列時為 2）
 */
export function toImportRows (body, columns, { amountRule = 'signed', dateOrder = 'ymd', today, firstLine = 2 }) {
  const col = (row, field) => {
    const i = columns.indexOf(field)
    return i < 0 ? '' : unguard(String(row[i] ?? '').trim())
  }
  return body.map((r, idx) => {
    const rawDate = col(r, 'date')
    const date = toYmd(rawDate, dateOrder, today) ?? rawDate
    let kind = parseKind(col(r, 'kind'))
    let amount = null
    if (amountRule === 'split' && !kind) {
      const inc = num(col(r, 'income'))
      const exp = num(col(r, 'expense'))
      if (inc && Number(inc) !== 0) { kind = 'income'; amount = abs(inc) } else if (exp && Number(exp) !== 0) { kind = 'expense'; amount = abs(exp) }
    } else {
      let a = num(col(r, 'amount'))
      if ((a === '' || a === null) && amountRule === 'split') a = num(col(r, 'income')) || num(col(r, 'expense'))
      if (a !== '' && a !== null) {
        const neg = Number(a) < 0
        amount = abs(a)
        if (!kind) {
          if (col(r, 'to_account')) kind = 'transfer'
          else if (amountRule === 'positive_expense') kind = neg ? 'income' : 'expense'
          else kind = neg ? 'expense' : 'income'
        }
      }
    }
    if (amount !== null && Number(amount) === 0) amount = '0'
    const category = [col(r, 'category'), col(r, 'subcategory')].filter(Boolean).join(' › ')
    const toAmount = num(col(r, 'to_amount'))
    const fee = num(col(r, 'fee'))
    return {
      line: firstLine + idx,
      date,
      kind: kind ?? '',
      title: col(r, 'title'),
      category,
      account: col(r, 'account'),
      amount,
      to_account: col(r, 'to_account'),
      to_amount: toAmount ? abs(toAmount) : null,
      fee: fee && Number(fee) !== 0 ? abs(fee) : null,
      note: col(r, 'note'),
      tags: col(r, 'tags').split(/[\s,，]+/).filter(Boolean)
    }
  })
}
