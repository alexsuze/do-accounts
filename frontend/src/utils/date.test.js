import { test } from 'node:test'
import assert from 'node:assert/strict'
import { today, thisMonth, shiftMonth, monthRange, weekday, shortDate, isValidDate } from './date.js'

test('today 以台北時間為準', () => {
  // UTC 11/08 17:30 = 台北 11/09 01:30
  assert.equal(today(new Date('2026-11-08T17:30:00Z')), '2026-11-09')
  assert.equal(thisMonth(new Date('2026-11-30T16:00:00Z')), '2026-12')
})

test('shiftMonth 跨年', () => {
  assert.equal(shiftMonth('2026-01', -1), '2025-12')
  assert.equal(shiftMonth('2026-12', 1), '2027-01')
  assert.equal(shiftMonth('2026-11', 0), '2026-11')
})

test('monthRange 含閏年', () => {
  assert.deepEqual(monthRange('2026-11'), { from: '2026-11-01', to: '2026-11-30' })
  assert.deepEqual(monthRange('2028-02'), { from: '2028-02-01', to: '2028-02-29' })
})

test('weekday 與 shortDate', () => {
  assert.equal(weekday('2026-11-09'), '一')
  assert.equal(shortDate('2026-11-09'), '11/09')
})

test('isValidDate', () => {
  assert.equal(isValidDate('2026-02-28'), true)
  assert.equal(isValidDate('2026-02-30'), false)
  assert.equal(isValidDate('2026/02/01'), false)
  assert.equal(isValidDate(undefined), false)
})
