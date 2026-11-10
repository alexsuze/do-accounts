// 全域鍵盤快捷鍵（SA FR-11.4）：純函式，方便 node --test

/** 正在輸入文字（輸入框、下拉選單、可編輯區）時不觸發快捷鍵 */
export function isTyping (target) {
  if (!target) return false
  const tag = (target.tagName || '').toUpperCase()
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || !!target.isContentEditable
}

/**
 * 鍵盤事件 → 動作名稱；不是快捷鍵時回傳 null
 * N：記帳；/：搜尋明細；?：快捷鍵說明
 */
export function shortcutFor (e) {
  if (e.ctrlKey || e.metaKey || e.altKey || e.isComposing) return null
  if (isTyping(e.target)) return null
  if (e.key === 'n' || e.key === 'N') return e.shiftKey ? null : 'new'
  if (e.key === '/') return 'search'
  if (e.key === '?') return 'help'
  return null
}

/** 說明清單（快捷鍵說明視窗） */
export const SHORTCUTS = [
  { keys: ['N'], label: '記帳' },
  { keys: ['/'], label: '搜尋明細' },
  { keys: ['?'], label: '顯示快捷鍵說明' },
  { keys: ['Esc'], label: '關閉視窗' },
  { keys: ['Ctrl', 'Enter'], label: '記帳視窗：儲存' },
  { keys: ['Enter'], label: '表格編輯：在最後一列新增一列' },
  { keys: ['Ctrl', 'D'], label: '表格編輯：複製上一列' },
  { keys: ['Ctrl', 'S'], label: '表格編輯：儲存全部' }
]
