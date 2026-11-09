import { test } from 'node:test'
import assert from 'node:assert/strict'
import { afterTrade, settlement, twFee, twTax } from './invest.js'

test('台股手續費：0.1425%、折扣、無條件捨去、最低 20', () => {
  assert.equal(twFee(19580, 'TW'), 27)
  assert.equal(twFee(1000, 'TW'), 20)
  assert.equal(twFee(1000000, 'TWO', 0.6), 855)
  assert.equal(twFee(1000, 'US'), 0)
})

test('證交稅：個股 0.3%、ETF 0.1%', () => {
  assert.equal(twTax(100000, 'TW', 'stock'), 300)
  assert.equal(twTax(96175, 'TW', 'etf'), 96)
  assert.equal(twTax(100000, 'US', 'stock'), 0)
})

test('交割金額', () => {
  assert.deepEqual(settlement('buy', { quantity: '100', price: '195.80', fee: '27', tax: '0' }), { gross: 19580, cash: -19607 })
  assert.deepEqual(settlement('sell', { quantity: '500', price: '200', fee: '142', tax: '100' }), { gross: 100000, cash: 99758 })
  assert.deepEqual(settlement('cash_dividend', { quantity: '1000', price: '1', fee: '10' }), { gross: 1000, cash: 990 })
  assert.deepEqual(settlement('fee', { fee: '50' }), { gross: 0, cash: -50 })
  assert.equal(settlement('split', {}), null)
})

test('交易後持股與均價', () => {
  const h = { quantity: '1000', cost: '158200' }
  assert.deepEqual(afterTrade('buy', h, { quantity: '100', price: '195.8', fee: '28' }), { quantity: 1100, avg: (158200 + 19580 + 28) / 1100 })
  const s = afterTrade('sell', h, { quantity: '1200', price: '192.35' })
  assert.equal(s.oversell, true)
  assert.equal(afterTrade('stock_dividend', h, { quantity: '50' }).avg, 158200 / 1050)
  assert.deepEqual(afterTrade('split', h, { quantity: '4' }), { quantity: 4000, avg: 39.55 })
  assert.equal(afterTrade('buy', null, { quantity: '10', price: '5' }).avg, 5)
  assert.deepEqual(afterTrade('opening', null, { quantity: '1000', price: '150' }), { quantity: 1000, avg: 150 })
  assert.equal(settlement('opening', { quantity: '1000', price: '150' }), null, '期初持股沒有現金')
})
