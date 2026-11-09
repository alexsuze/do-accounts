import { test } from 'node:test'
import assert from 'node:assert/strict'
import { resolveDark, readMode, saveMode, readPnl, nextToggleMode, THEME_KEY } from './theme.js'

const memory = (init = {}) => {
  const m = new Map(Object.entries(init))
  return { getItem: k => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) }
}
const broken = { getItem () { throw new Error('denied') }, setItem () { throw new Error('denied') } }

test('resolveDark：明確設定優先，system 依作業系統', () => {
  assert.equal(resolveDark('dark', false), true)
  assert.equal(resolveDark('light', true), false)
  assert.equal(resolveDark('system', true), true)
  assert.equal(resolveDark('system', false), false)
  assert.equal(resolveDark(undefined, true), true)
})

test('readMode：不合法或讀取失敗時回 system', () => {
  assert.equal(readMode(memory({ [THEME_KEY]: 'light' })), 'light')
  assert.equal(readMode(memory({ [THEME_KEY]: 'neon' })), 'system')
  assert.equal(readMode(memory()), 'system')
  assert.equal(readMode(broken), 'system')
  assert.equal(readMode(undefined), 'system')
})

test('saveMode：寫入失敗不丟錯', () => {
  const s = memory()
  saveMode(s, 'dark')
  assert.equal(readMode(s), 'dark')
  assert.doesNotThrow(() => saveMode(broken, 'dark'))
})

test('readPnl：預設綠收紅支', () => {
  assert.equal(readPnl(memory()), 'green')
  assert.equal(readPnl(memory({ 'do-accounts:pnl': 'red' })), 'red')
  assert.equal(readPnl(broken), 'green')
})

test('nextToggleMode：切到與目前相反', () => {
  assert.equal(nextToggleMode(true), 'light')
  assert.equal(nextToggleMode(false), 'dark')
})
