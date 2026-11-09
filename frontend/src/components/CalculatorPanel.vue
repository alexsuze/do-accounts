<template>
  <div v-if="ui.calculator.open" ref="panel" role="dialog" aria-label="計算機" tabindex="-1"
    class="glow fixed z-[70] flex w-[280px] flex-col gap-3 rounded-2xl border border-line bg-panel p-4 text-text shadow-2xl outline-none"
    :style="{ left: pos.x + 'px', top: pos.y + 'px' }" @keydown="onKey">
    <div class="flex cursor-move touch-none items-center gap-2 select-none" @pointerdown="startDrag">
      <GripVertical :size="14" class="text-muted" aria-hidden="true" />
      <span class="lbl mr-auto">Calculator · 可拖曳</span>
      <button type="button" aria-label="關閉計算機" class="flex size-8 items-center justify-center rounded-lg text-muted hover:bg-panel2 hover:text-text" @click="ui.closeCalculator()">
        <X :size="16" aria-hidden="true" />
      </button>
    </div>
    <div class="rounded-[10px] border border-line bg-panel2 p-3 text-right">
      <div class="num min-h-5 truncate text-[13px] text-muted">{{ expr || ' ' }}</div>
      <div class="num truncate text-[28px] font-semibold" :class="error ? 'text-exp text-base' : 'text-accent'" aria-live="polite">{{ error || display }}</div>
    </div>
    <div class="grid grid-cols-4 gap-1.5">
      <button v-for="k in keys" :key="k" type="button" :aria-label="aria[k] || k"
        class="num h-12 rounded-[10px] border border-line bg-panel2 text-lg hover:brightness-110"
        :class="k === '=' ? 'border-accent bg-accent-soft text-accent' : ops.includes(k) ? 'text-accent' : 'text-text'" @click="press(k)">{{ k }}</button>
    </div>
    <AppButton v-if="ui.calculator.target" :disabled="!!error || !result" @click="apply">帶入金額</AppButton>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { GripVertical, X } from 'lucide-vue-next'
import AppButton from './ui/AppButton.vue'
import { useUiStore } from '../stores/ui'
import { evaluate } from '../utils/expr'

// 全域浮動計算機（SA FR-11.1、SD 4.8）：拖曳（pointer events，滑鼠與觸控）、鍵盤輸入（焦點在面板時）、帶入金額
const ui = useUiStore()
const panel = ref(null)
const expr = ref('')
const pos = ref({ x: 0, y: 0 })
const keys = ['C', '(', ')', '÷', '7', '8', '9', '×', '4', '5', '6', '−', '1', '2', '3', '+', '0', '.', '⌫', '=']
const ops = ['÷', '×', '−', '+', '(', ')']
const aria = { C: '清除', '⌫': '退格', '÷': '除', '×': '乘', '−': '減', '+': '加', '=': '等於' }

const result = computed(() => { try { return evaluate(expr.value) } catch { return null } })
const error = computed(() => { try { evaluate(expr.value); return '' } catch (e) { return expr.value ? e.message : '' } })
const display = computed(() => result.value === null ? '0' : result.value.toString())

watch(() => ui.calculator.open, async (open) => {
  if (!open) return
  expr.value = ui.calculator.initial || ''
  pos.value = { x: Math.max(8, window.innerWidth / 2 - 140), y: Math.max(8, window.innerHeight / 2 - 240) }
  await nextTick()
  panel.value?.focus()
})

function press (k) {
  if (k === 'C') expr.value = ''
  else if (k === '⌫') expr.value = expr.value.slice(0, -1)
  else if (k === '=') { if (result.value) expr.value = result.value.toString() }
  else expr.value += k
}

function onKey (e) {
  const map = { '*': '×', '/': '÷', '-': '−', Enter: '=', Backspace: '⌫', c: 'C', C: 'C' }
  if (e.key === 'Escape') { ui.closeCalculator(); return }
  const k = map[e.key] ?? e.key
  if (/^[0-9.+()]$/.test(k) || keys.includes(k)) {
    e.preventDefault()
    if (k === '=' && e.ctrlKey && ui.calculator.target) apply()
    else press(k)
  }
}

function apply () {
  if (!result.value) return
  ui.calculator.target?.(result.value.toString())
  ui.closeCalculator()
}

function startDrag (e) {
  if (e.target.closest('button')) return
  const start = { x: e.clientX - pos.value.x, y: e.clientY - pos.value.y }
  const move = (ev) => {
    pos.value = {
      x: Math.min(Math.max(0, ev.clientX - start.x), window.innerWidth - 120),
      y: Math.min(Math.max(0, ev.clientY - start.y), window.innerHeight - 80)
    }
  }
  const up = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up) }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}
</script>
