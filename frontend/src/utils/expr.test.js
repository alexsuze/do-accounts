import { test } from 'node:test'
import assert from 'node:assert/strict'
import { evaluate, isExpression, toAmountString } from './expr.js'

const ev = (s) => evaluate(s).toString()

test('四則運算與優先順序', () => {
  assert.equal(ev('120+35'), '155')
  assert.equal(ev('120+35*2'), '190')
  assert.equal(ev('(120+35)*2'), '310')
  assert.equal(ev('100/4-5'), '20')
  assert.equal(ev(' 1,200 + 0.5 '), '1200.5', '允許千分位逗號與空白')
  assert.equal(ev('3×4÷2−1'), '5', '計算機符號')
})

test('沒有浮點誤差', () => {
  assert.equal(ev('0.1+0.2'), '0.3')
  assert.equal(ev('1.1*3'), '3.3')
})

test('正負號', () => {
  assert.equal(ev('-5+10'), '5')
  assert.equal(ev('2*-3'), '-6')
  assert.equal(ev('+7'), '7')
})

test('錯誤處理', () => {
  assert.equal(evaluate(''), null)
  assert.equal(evaluate('   '), null)
  assert.throws(() => evaluate('1+'), /不完整/)
  assert.throws(() => evaluate('(1+2'), /括號/)
  assert.throws(() => evaluate('1/0'), /除以 0/)
  assert.throws(() => evaluate('alert(1)'), /無法辨識/)
  assert.throws(() => evaluate('1 2'), /附近有誤/)
})

test('isExpression', () => {
  assert.equal(isExpression('120'), false)
  assert.equal(isExpression('-120'), false)
  assert.equal(isExpression('120+35'), true)
})

test('toAmountString 依位數四捨五入', () => {
  assert.equal(toAmountString('100/3', 0), '33')
  assert.equal(toAmountString('100/3', 2), '33.33')
  assert.equal(toAmountString('2.345', 2), '2.35')
  assert.equal(toAmountString('', 0), null)
})
