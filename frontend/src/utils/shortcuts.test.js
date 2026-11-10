import { test } from 'node:test'
import assert from 'node:assert/strict'
import { isTyping, shortcutFor } from './shortcuts.js'

const ev = (key, extra = {}) => ({ key, ctrlKey: false, metaKey: false, altKey: false, shiftKey: false, isComposing: false, target: { tagName: 'BODY' }, ...extra })

test('快捷鍵對應', () => {
  assert.equal(shortcutFor(ev('n')), 'new')
  assert.equal(shortcutFor(ev('N')), 'new')
  assert.equal(shortcutFor(ev('/')), 'search')
  assert.equal(shortcutFor(ev('?', { shiftKey: true })), 'help')
  assert.equal(shortcutFor(ev('x')), null)
})

test('輸入中、組字中或按著修飾鍵時不觸發', () => {
  assert.equal(shortcutFor(ev('n', { target: { tagName: 'INPUT' } })), null)
  assert.equal(shortcutFor(ev('n', { target: { tagName: 'DIV', isContentEditable: true } })), null)
  assert.equal(shortcutFor(ev('n', { isComposing: true })), null)
  assert.equal(shortcutFor(ev('n', { ctrlKey: true })), null)
  assert.equal(shortcutFor(ev('n', { metaKey: true })), null)
  assert.equal(isTyping({ tagName: 'textarea' }), true)
  assert.equal(isTyping(null), false)
})
