import { test } from 'node:test'
import assert from 'node:assert/strict'
import { budgetTone, compact, dailyCap, donutSegments, linePath, niceMax, scale } from './charts.js'

test('niceMax 取好看的上限', () => {
  assert.equal(niceMax(0), 1)
  assert.equal(niceMax(7), 10)
  assert.equal(niceMax(1800), 2000)
  assert.equal(niceMax(2100), 2500)
  assert.equal(niceMax(4300), 5000)
  assert.equal(niceMax(5000), 5000)
})

test('scale 線性對應', () => {
  const y = scale(0, 100, 200, 0)
  assert.equal(y(0), 200)
  assert.equal(y(50), 100)
  assert.equal(scale(5, 5, 0, 10)(5), 0) // 範圍為 0 時不除以 0
})

test('donutSegments 依比例切段並接續 offset', () => {
  const s = donutSegments([1, 3], 100)
  assert.equal(s[0].dash, '25.00 100.00')
  assert.equal(s[0].offset, '0.00')
  assert.equal(s[1].dash, '75.00 100.00')
  assert.equal(s[1].offset, '-25.00')
  assert.equal(donutSegments([0, 0], 100)[0].length, 0)
})

test('budgetTone 依門檻變色', () => {
  assert.equal(budgetTone('42.5'), 'inc')
  assert.equal(budgetTone('80.0'), 'warn')
  assert.equal(budgetTone('100.0'), 'warn')
  assert.equal(budgetTone('100.1'), 'exp')
  assert.equal(budgetTone(70, 60), 'warn')
})

test('compact 簡短金額', () => {
  assert.equal(compact(68500), '68.5k')
  assert.equal(compact(1250000), '1.25M')
  assert.equal(compact(950), '950')
  assert.equal(compact(-12000), '−12k')
})

test('linePath', () => {
  assert.equal(linePath([[0, 1], [2, 3.25]]), 'M0.0 1.0L2.0 3.3')
})

test('dailyCap 遇到特別大的一天改用第二大', () => {
  assert.equal(dailyCap([]), 1)
  assert.equal(dailyCap([100, 300, 250]), 500)
  assert.equal(dailyCap([18000, 2000, 300]), 2500)
})
