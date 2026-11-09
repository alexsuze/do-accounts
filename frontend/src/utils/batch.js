// 表格模式（多筆編輯）的列狀態、檢查與送出內容（SA FR-3.10～3.20、SD 4.2.1）。純函式，方便 node --test
import { digitsOf } from './money.js'
import { decimalsError, evaluate, toAmountString } from './expr.js'
import { isValidDate } from './date.js'

export const MAX_ROWS = 100
/** 表格欄位，順序就是畫面與「從 Excel 貼上」的欄位順序 */
export const COLUMNS = [
  { field: 'date', label: '日期' },
  { field: 'kind', label: '類型' },
  { field: 'title', label: '項目' },
  { field: 'category_id', label: '分類' },
  { field: 'account_id', label: '帳戶' },
  { field: 'amount', label: '金額' },
  { field: 'to_account_id', label: '轉入帳戶' },
  { field: 'to_amount', label: '轉入金額' },
  { field: 'tags', label: '標籤' },
  { field: 'note', label: '備註' }
]
const FIELDS = COLUMNS.map(c => c.field)

let seq = 0
const key = () => `r${Date.now().toString(36)}${(++seq).toString(36)}`

const blankFields = () => ({ date: '', kind: 'expense', title: '', category_id: null, account_id: null, amount: '', to_account_id: null, to_amount: '', tags: '', note: '' })

/** 新的一列。defaults 通常是上一列的日期、類型、帳戶（SA FR-3.13） */
export function newRow (defaults = {}) {
  return { key: key(), id: null, version: null, original: null, deleted: false, hidden: {}, ...blankFields(), ...defaults }
}

/** 既有交易 → 一列；original 保存載入時的值，用來判斷是否修改、可還原 */
export function fromTransaction (t) {
  const e = (role) => t.entries.find(x => x.role === role)
  const main = e('main') ?? e('transfer_out')
  const tin = e('transfer_in')
  const fee = e('fee')
  const fields = {
    date: t.date,
    kind: t.kind,
    title: t.kind === 'transfer' && t.title === '轉帳' ? '' : t.title,
    category_id: t.category_id ?? null,
    account_id: main?.account_id ?? null,
    amount: main ? String(Math.abs(Number(main.amount))) : '',
    to_account_id: tin?.account_id ?? null,
    to_amount: tin ? String(tin.amount) : '',
    tags: (t.tags ?? []).map(x => '#' + x).join(' '),
    note: t.note ?? ''
  }
  // 表格沒有的欄位（轉帳手續費）原樣保留，避免儲存時被清掉
  const hidden = fee ? { fee: String(-Number(fee.amount)), fee_category_id: fee.category_id } : {}
  return { key: key(), id: t.id, version: t.version, original: { ...fields }, deleted: false, hidden, ...fields }
}

const same = (a, b) => String(a ?? '') === String(b ?? '')

export function changedFields (r) {
  if (!r.original) return new Set()
  return new Set(FIELDS.filter(f => !same(r[f], r.original[f])))
}

/** 新列只要項目與金額都沒填就算空白（日期、類型、帳戶有預設值） */
export function isBlank (r) {
  return !r.id && !String(r.title ?? '').trim() && !String(r.amount ?? '').trim() && !String(r.to_amount ?? '').trim()
}

/** clean / modified / deleted / new / blank */
export function rowStatus (r) {
  if (r.id) return r.deleted ? 'deleted' : changedFields(r).size ? 'modified' : 'clean'
  return isBlank(r) ? 'blank' : 'new'
}

export function revertRow (r) {
  if (!r.original) return r
  return { ...r, ...r.original, deleted: false }
}

/**
 * 前端即時檢查（最終以後端為準）。ctx：{ accountsById, categoriesById }
 * @return {{ [field]: string }} 沒有錯誤時是空物件
 */
export function validateRow (r, ctx) {
  const err = {}
  const from = ctx.accountsById[r.account_id]
  const to = ctx.accountsById[r.to_account_id]
  if (!isValidDate(r.date)) err.date = '日期需為 YYYY-MM-DD'
  if (!from) err.account_id = '請選擇帳戶'
  else if (from.archived_at && (!r.original || r.original.account_id !== r.account_id)) err.account_id = '帳戶已封存'
  const minDate = [from?.opening_date, r.kind === 'transfer' ? to?.opening_date : null].filter(Boolean).sort().at(-1)
  if (!err.date && minDate && r.date < minDate) err.date = `不可早於開戶日期 ${minDate}`
  amount(r.amount, from?.currency, '金額', true, err, 'amount')
  if (r.kind === 'transfer') {
    if (!to) err.to_account_id = '請選擇轉入帳戶'
    else if (to.id === r.account_id) err.to_account_id = '不能與轉出帳戶相同'
    if (from && to && from.currency !== to.currency) amount(r.to_amount, to.currency, '轉入金額', true, err, 'to_amount')
  } else {
    if (!String(r.title ?? '').trim()) err.title = '請輸入項目'
    const c = ctx.categoriesById[r.category_id]
    if (!c) err.category_id = '請選擇分類'
    else if (c.kind !== r.kind) err.category_id = `不是${r.kind === 'income' ? '收入' : '支出'}分類`
  }
  if (!['expense', 'income', 'transfer'].includes(r.kind)) err.kind = '類型不正確'
  return err
}

function amount (raw, currency, label, required, err, field) {
  if (!String(raw ?? '').trim()) { if (required) err[field] = `請輸入${label}`; return }
  const cur = currency ?? 'TWD'
  try {
    const v = toAmountString(raw, digitsOf(cur))
    if (Number(v) <= 0) err[field] = `${label}必須大於 0`
    else if (decimalsError(raw, digitsOf(cur), cur)) err[field] = decimalsError(raw, digitsOf(cur), cur)
  } catch (e) { err[field] = e.message }
}

const tagsOf = (s) => String(s ?? '').split(/[\s,，]+/).map(x => x.replace(/^#/, '')).filter(Boolean)

/** 一列 → API 的 BatchRow（row 為畫面上的列號，從 1 開始） */
export function toPayload (r, rowNo, ctx) {
  const from = ctx.accountsById[r.account_id]
  const to = ctx.accountsById[r.to_account_id]
  const body = {
    row: rowNo,
    kind: r.kind,
    date: r.date,
    title: String(r.title ?? '').trim(),
    note: String(r.note ?? '').trim(),
    tags: tagsOf(r.tags),
    account_id: r.account_id,
    amount: toAmountString(r.amount, digitsOf(from.currency))
  }
  if (r.kind === 'transfer') {
    body.to_account_id = r.to_account_id
    if (to && to.currency !== from.currency) body.to_amount = toAmountString(r.to_amount, digitsOf(to.currency))
    if (r.hidden?.fee) { body.fee = r.hidden.fee; body.fee_category_id = r.hidden.fee_category_id }
  } else {
    body.category_id = r.category_id
  }
  if (r.id) { body.id = r.id; body.version = r.version }
  return body
}

/**
 * 整張表 → 送出內容與檢查結果。空白的新列略過；只有變更的列才送出。
 * @return {{ creates, updates, deletes, errors: { [key]: { [field]: msg } }, count }}
 */
export function buildBatch (rows, ctx) {
  const out = { creates: [], updates: [], deletes: [], errors: {}, count: 0 }
  rows.forEach((r, i) => {
    const status = rowStatus(r)
    const rowNo = i + 1
    if (status === 'blank' || status === 'clean') return
    out.count++
    if (status === 'deleted') { out.deletes.push({ row: rowNo, id: r.id, version: r.version }); return }
    const err = validateRow(r, ctx)
    if (Object.keys(err).length) { out.errors[r.key] = err; return }
    ;(status === 'new' ? out.creates : out.updates).push(toPayload(r, rowNo, ctx))
  })
  return out
}

/** 本次合計（新增＋修改後，不含刪除），依幣別分開；轉帳只計筆數（SA FR-3.15） */
export function batchTotals (rows, ctx) {
  const byCurrency = {}
  let transfers = 0
  for (const r of rows) {
    const s = rowStatus(r)
    if (s !== 'new' && s !== 'modified') continue
    if (r.kind === 'transfer') { transfers++; continue }
    const cur = ctx.accountsById[r.account_id]?.currency
    let v
    try { v = evaluate(r.amount) } catch { v = null }
    if (!cur || !v) continue
    const t = byCurrency[cur] ??= { income: 0, expense: 0 }
    t[r.kind === 'income' ? 'income' : 'expense'] += Number(v.toString())
  }
  return { byCurrency, transfers }
}

export function summarize (rows) {
  const s = { new: 0, modified: 0, deleted: 0 }
  rows.forEach(r => { const st = rowStatus(r); if (st in s) s[st]++ })
  return s
}
