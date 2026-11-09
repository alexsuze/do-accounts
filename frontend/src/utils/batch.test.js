import { test } from 'node:test'
import assert from 'node:assert/strict'
import { newRow, fromTransaction, rowStatus, changedFields, revertRow, validateRow, buildBatch, batchTotals, summarize } from './batch.js'

const accountsById = {
  cash: { id: 'cash', currency: 'TWD', opening_date: '2026-01-01' },
  bank: { id: 'bank', currency: 'TWD', opening_date: '2026-01-01' },
  usd: { id: 'usd', currency: 'USD', opening_date: '2026-03-01' },
  old: { id: 'old', currency: 'TWD', opening_date: '2026-01-01', archived_at: '2026-06-01T00:00:00Z' }
}
const categoriesById = { food: { id: 'food', kind: 'expense' }, salary: { id: 'salary', kind: 'income' } }
const ctx = { accountsById, categoriesById }
const tx = {
  id: 't1', version: 3, kind: 'expense', date: '2026-11-08', title: '晚餐', category_id: 'food', note: '', tags: ['聚餐'],
  entries: [{ account_id: 'cash', amount: '-260', role: 'main' }]
}
const transfer = {
  id: 't2', version: 0, kind: 'transfer', date: '2026-11-08', title: '轉帳', category_id: null, note: '', tags: [],
  entries: [{ account_id: 'bank', amount: '-32415', role: 'transfer_out' }, { account_id: 'usd', amount: '1000', role: 'transfer_in' }, { account_id: 'bank', amount: '-30', role: 'fee', category_id: 'fee' }]
}
const ok = (patch = {}) => ({ ...newRow({ date: '2026-11-09', account_id: 'cash' }), title: '咖啡', category_id: 'food', amount: '85', ...patch })

test('列狀態：新增／空白／未變更／修改／刪除，並可還原', () => {
  assert.equal(rowStatus(newRow({ date: '2026-11-09', account_id: 'cash' })), 'blank', '只有預設值算空白')
  assert.equal(rowStatus(ok()), 'new')
  const r = fromTransaction(tx)
  assert.equal(rowStatus(r), 'clean')
  assert.equal(r.tags, '#聚餐')
  r.amount = '300'
  assert.equal(rowStatus(r), 'modified')
  assert.deepEqual([...changedFields(r)], ['amount'])
  assert.equal(rowStatus(revertRow(r)), 'clean')
  assert.equal(rowStatus({ ...r, deleted: true }), 'deleted')
})

test('轉帳：預設標題清空、手續費放在隱藏欄位並會送出', () => {
  const r = fromTransaction(transfer)
  assert.equal(r.title, '')
  assert.equal(r.amount, '32415')
  assert.equal(r.to_amount, '1000')
  assert.deepEqual(r.hidden, { fee: '30', fee_category_id: 'fee' })
  r.note = '換匯'
  const b = buildBatch([r], ctx)
  assert.equal(b.updates[0].fee, '30')
  assert.equal(b.updates[0].to_amount, '1000.00', 'USD 兩位小數')
  assert.equal(b.updates[0].version, 0)
})

test('檢查：必填、日期、開戶日期、分類類型、金額位數、轉帳', () => {
  assert.deepEqual(validateRow(ok(), ctx), {})
  assert.ok(validateRow(ok({ title: '' }), ctx).title)
  assert.ok(validateRow(ok({ date: '2026/11/09' }), ctx).date)
  assert.match(validateRow(ok({ date: '2025-12-30' }), ctx).date, /2026-01-01/)
  assert.ok(validateRow(ok({ category_id: 'salary' }), ctx).category_id)
  assert.ok(validateRow(ok({ amount: '12.5' }), ctx).amount, 'TWD 不能有小數')
  assert.ok(validateRow(ok({ amount: '1+' }), ctx).amount)
  assert.ok(validateRow(ok({ amount: '0' }), ctx).amount)
  assert.ok(validateRow(ok({ account_id: 'old' }), ctx).account_id, '封存帳戶不能新增')
  const t = ok({ kind: 'transfer', title: '', category_id: null, account_id: 'bank', to_account_id: 'usd', amount: '1000', date: '2026-02-01' })
  const e = validateRow(t, ctx)
  assert.match(e.date, /2026-03-01/, '轉帳兩邊的開戶日期都要檢查')
  assert.ok(e.to_amount, '跨幣別要填轉入金額')
  assert.ok(validateRow({ ...t, to_account_id: 'bank' }, ctx).to_account_id)
})

test('buildBatch：只送變更、空白略過、列號從 1 開始、算式先算好', () => {
  const clean = fromTransaction(tx)
  const del = { ...fromTransaction(transfer), deleted: true }
  const bad = ok({ title: '' })
  const rows = [clean, ok({ amount: '120+35', tags: '#加班 公司' }), newRow({ date: '2026-11-09' }), del, bad]
  const b = buildBatch(rows, ctx)
  assert.equal(b.count, 3)
  assert.equal(b.creates.length, 1)
  assert.equal(b.creates[0].row, 2)
  assert.equal(b.creates[0].amount, '155')
  assert.deepEqual(b.creates[0].tags, ['加班', '公司'])
  assert.deepEqual(b.deletes, [{ row: 4, id: 't2', version: 0 }])
  assert.deepEqual(Object.keys(b.errors), [bad.key])
})

test('合計與摘要', () => {
  const rows = [ok({ amount: '100' }), ok({ amount: '50', kind: 'income', category_id: 'salary' }), { ...fromTransaction(tx), amount: '300' }, { ...fromTransaction(transfer), note: 'x' }, { ...fromTransaction(tx), deleted: true }]
  const t = batchTotals(rows, ctx)
  assert.deepEqual(t.byCurrency.TWD, { income: 50, expense: 400 })
  assert.equal(t.transfers, 1)
  assert.deepEqual(summarize(rows), { new: 2, modified: 2, deleted: 1 })
})
