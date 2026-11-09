import { test } from 'node:test'
import assert from 'node:assert/strict'
import { decodeBytes, detectDelimiter, parseDelimited } from './csv.js'
import { columnsFromTemplate, guessColumns, templateFromColumns, toImportRows, unguard, BUILTIN_TEMPLATE } from './importer.js'

test('decodeBytes：UTF-8（去 BOM）與 Big5', () => {
  const utf8 = new Uint8Array([0xEF, 0xBB, 0xBF, ...new TextEncoder().encode('日期,金額')])
  assert.deepEqual(decodeBytes(utf8), { text: '日期,金額', encoding: 'UTF-8' })
  const big5 = new Uint8Array([0xA4, 0xE9, 0xB4, 0xC1]) // 「日期」
  assert.deepEqual(decodeBytes(big5), { text: '日期', encoding: 'Big5' })
})

test('detectDelimiter 與 parseDelimited', () => {
  assert.equal(detectDelimiter('a,b,"c,d"\n1,2,3'), ',')
  assert.equal(detectDelimiter('a\tb\tc'), '\t')
  assert.equal(detectDelimiter('a;b;c'), ';')
  assert.equal(detectDelimiter('單一欄'), ',')
  assert.deepEqual(parseDelimited('日期,項目\r\n2026/10/03,"全聯, 福利中心"\n2026/10/04,"多\n行"\n\n'), [
    ['日期', '項目'], ['2026/10/03', '全聯, 福利中心'], ['2026/10/04', '多\n行']])
  assert.deepEqual(parseDelimited('a, "b ""q"""'), [['a', 'b "q"']])
})

test('guessColumns 依標題猜對應', () => {
  assert.deepEqual(guessColumns(['交易日期', '摘要', '支出金額', '存入金額', '餘額', '備註']),
    ['date', 'title', 'expense', 'income', 'ignore', 'note'])
  assert.deepEqual(guessColumns(['日期', '類型', '項目', '大類', '子類', '帳戶', '金額', '幣別', '轉入帳戶', '轉入金額', '手續費', '標籤', '備註']),
    ['date', 'kind', 'title', 'category', 'subcategory', 'account', 'amount', 'ignore', 'to_account', 'to_amount', 'fee', 'tags', 'note'])
})

test('範本來回轉換', () => {
  const headers = ['交易日期', '摘要', '餘額']
  const t = templateFromColumns(headers, ['date', 'title', 'ignore'], { amountRule: 'split', dateOrder: 'ymd', defaultAccountId: 'a1' })
  assert.deepEqual(t, { by_header: { 交易日期: 'date', 摘要: 'title' }, amount_rule: 'split', date_order: 'ymd', default_account_id: 'a1' })
  assert.deepEqual(columnsFromTemplate(['摘要', '新欄位', '交易日期'], t), ['title', 'ignore', 'date'])
})

test('toImportRows：單一金額欄、分開欄、信用卡、類型欄、轉帳', () => {
  const today = '2026-11-09'
  const signed = toImportRows([['2026/10/03', '全聯', '-1,286'], ['115/10/05', '薪資', '68,500'], ['13/40', '壞', 'abc']],
    ['date', 'title', 'amount'], { amountRule: 'signed', today })
  assert.equal(signed[0].line, 2)
  assert.equal(signed[0].date, '2026-10-03')
  assert.equal(signed[0].kind, 'expense')
  assert.equal(signed[0].amount, '1286')
  assert.equal(signed[1].kind, 'income')
  assert.equal(signed[1].date, '2026-10-05')
  assert.equal(signed[2].date, '13/40', '無法解析的日期原樣送出，由後端標錯')
  assert.equal(signed[2].amount, null)

  const split = toImportRows([['2026/10/03', '全聯', '1,286', ''], ['2026/10/05', '薪資', '', '68500']],
    ['date', 'title', 'expense', 'income'], { amountRule: 'split', today })
  assert.deepEqual([split[0].kind, split[0].amount, split[1].kind, split[1].amount], ['expense', '1286', 'income', '68500'])

  const card = toImportRows([['10/03/2026', 'Netflix', '390'], ['10/05/2026', '退款', '-90']], ['date', 'title', 'amount'],
    { amountRule: 'positive_expense', dateOrder: 'mdy', today })
  assert.deepEqual([card[0].date, card[0].kind, card[1].kind, card[1].amount], ['2026-10-03', 'expense', 'income', '90'])

  const own = toImportRows([['2026-11-01', '支出', "'=HYPERLINK(\"x\")", '餐飲', '午餐', '錢包', '-120', 'TWD', '', '', '', '聚餐 出差', '']],
    columnsFromTemplate(['日期', '類型', '項目', '大類', '子類', '帳戶', '金額', '幣別', '轉入帳戶', '轉入金額', '手續費', '標籤', '備註'], BUILTIN_TEMPLATE.mapping),
    { today })
  assert.equal(own[0].title, '=HYPERLINK("x")')
  assert.equal(own[0].category, '餐飲 › 午餐')
  assert.equal(own[0].kind, 'expense')
  assert.equal(own[0].amount, '120')
  assert.deepEqual(own[0].tags, ['聚餐', '出差'])

  const tr = toImportRows([['2026-11-02', '', '國泰', '500', '錢包', '500', '15']],
    ['date', 'title', 'account', 'amount', 'to_account', 'to_amount', 'fee'], { today })
  assert.deepEqual([tr[0].kind, tr[0].to_account, tr[0].to_amount, tr[0].fee], ['transfer', '錢包', '500', '15'])
  assert.equal(unguard("'-5"), '-5')
  assert.equal(unguard("'abc"), "'abc")
})
