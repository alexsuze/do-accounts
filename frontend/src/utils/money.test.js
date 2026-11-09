import { test } from 'node:test'
import assert from 'node:assert/strict'
import { formatAmount, signOf, digitsOf } from './money.js'

test('依幣別小數位數四捨五入', () => {
  assert.equal(formatAmount('1234.5', 'TWD'), '1,235')
  assert.equal(formatAmount('1234.5', 'USD'), '1,234.50')
  assert.equal(formatAmount('99.994', 'USD'), '99.99')
  assert.equal(formatAmount('0.2', 'JPY'), '0')
  assert.equal(digitsOf('XYZ'), 2, '未知幣別預設 2 位')
})

test('正負號：負數用 U+2212，always 時正數加 +，零不加', () => {
  assert.equal(formatAmount('-120', 'TWD'), '−120')
  assert.equal(formatAmount('68500', 'TWD', { sign: 'always' }), '+68,500')
  assert.equal(formatAmount('0', 'TWD', { sign: 'always' }), '0')
  assert.equal(formatAmount('-0.004', 'USD', { sign: 'always' }), '0.00', '四捨五入後為零就不帶負號')
  assert.equal(formatAmount('-5', 'TWD', { sign: 'none' }), '5')
})

test('幣別符號與非法值', () => {
  assert.equal(formatAmount('1000', 'USD', { symbol: true }), 'US$ 1,000.00')
  assert.equal(formatAmount('-1000', 'TWD', { symbol: true }), '−NT$ 1,000')
  assert.equal(formatAmount('abc'), '—')
  assert.equal(formatAmount(undefined), '—')
})

test('signOf', () => {
  assert.equal(signOf('-1'), -1)
  assert.equal(signOf('2.5'), 1)
  assert.equal(signOf('0'), 0)
  assert.equal(signOf('x'), 0)
})
