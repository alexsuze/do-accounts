import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseClipboard, parseDate, parseAmount, parseKind, matchName } from './paste.js'

test('parseClipboard：Tab 與換行、去掉最後的空行、引號內的換行', () => {
  assert.deepEqual(parseClipboard('a\tb\n c \td\n'), [['a', 'b'], ['c', 'd']])
  assert.deepEqual(parseClipboard('a\r\nb'), [['a'], ['b']])
  assert.deepEqual(parseClipboard('"多\n行"\tx\n"say ""hi"""\ty'), [['多\n行', 'x'], ['say "hi"', 'y']])
  assert.deepEqual(parseClipboard(''), [])
})

test('parseDate：常見格式、今年、民國年、錯誤', () => {
  const today = '2026-11-09'
  assert.equal(parseDate('2026-11-09', today), '2026-11-09')
  assert.equal(parseDate('2026/11/9', today), '2026-11-09')
  assert.equal(parseDate('2026.1.5', today), '2026-01-05')
  assert.equal(parseDate('2026年11月9日', today), '2026-11-09')
  assert.equal(parseDate('11/9', today), '2026-11-09')
  assert.equal(parseDate('115/11/09', today), '2026-11-09')
  assert.equal(parseDate('民國115/1/2', today), '2026-01-02')
  assert.equal(parseDate('2026/02/30', today), null)
  assert.equal(parseDate('昨天', today), null)
})

test('parseAmount：千分位、幣別、負數表示法、算式', () => {
  assert.equal(parseAmount('1,286'), '1286')
  assert.equal(parseAmount('NT$ 1,286'), '1286')
  assert.equal(parseAmount('$12.50'), '12.50')
  assert.equal(parseAmount('-500'), '-500')
  assert.equal(parseAmount('(500)'), '-500')
  assert.equal(parseAmount('500-'), '-500')
  assert.equal(parseAmount('120 元'), '120')
  assert.equal(parseAmount('120+35'), '120+35')
  assert.equal(parseAmount(''), '')
  assert.equal(parseAmount('一百'), null)
})

test('parseKind', () => {
  assert.equal(parseKind('支出'), 'expense')
  assert.equal(parseKind('Income'), 'income')
  assert.equal(parseKind('轉帳'), 'transfer')
  assert.equal(parseKind(''), null)
  assert.equal(parseKind('退款'), null)
})

test('matchName：完全相同 → 去空白 → 包含（取最短）', () => {
  const items = [
    { id: 'food', names: ['餐飲'] },
    { id: 'lunch', names: ['餐飲 › 午餐', '午餐'] },
    { id: 'bank', names: ['國泰 薪轉'] },
    { id: 'bank2', names: ['國泰世華信用卡'] }
  ]
  assert.equal(matchName(items, '午餐'), 'lunch')
  assert.equal(matchName(items, '國泰薪轉'), 'bank', '忽略空白')
  assert.equal(matchName(items, '國泰'), 'bank', '包含比對取名稱最短的')
  assert.equal(matchName(items, '餐飲 › 午餐'), 'lunch')
  assert.equal(matchName(items, '交通'), null)
  assert.equal(matchName(items, ''), null)
})
