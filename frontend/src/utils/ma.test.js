import { test } from 'node:test'
import assert from 'node:assert/strict'
import { movingAverage } from './ma.js'

test('移動平均：資料不足為 null、之後為前 n 天平均', () => {
  assert.deepEqual(movingAverage([1, 2, 3, 4, 5], 3), [null, null, 2, 3, 4])
  assert.deepEqual(movingAverage([10], 5), [null])
  assert.deepEqual(movingAverage([], 5), [])
  assert.deepEqual(movingAverage([2, 4], 1), [2, 4])
})
