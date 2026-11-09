import { reactive } from 'vue'

// 全站通知（SD 5.5 ToastHost）。不放 Pinia：api.js 在 Pinia 建立前就可能用到
export const toasts = reactive([])
let seq = 0

function push (type, message, duration = 3000) {
  toasts.push({ id: ++seq, type, message, duration })
}

export function dismiss (id) {
  const i = toasts.findIndex(t => t.id === id)
  if (i >= 0) toasts.splice(i, 1)
}

export const toast = {
  success: (m) => push('success', m),
  error: (m) => push('error', m, 5000),
  warn: (m) => push('warn', m, 4000),
  info: (m) => push('info', m)
}
