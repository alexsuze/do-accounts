// 深淺色與收支顏色（SD 5.7）。純函式＋少量 DOM 操作；index.html 的 inline script 邏輯與 resolveDark 相同
export const THEME_KEY = 'do-accounts:theme'
export const PNL_KEY = 'do-accounts:pnl'
export const MODES = ['dark', 'light', 'system']

/** mode 為 dark / light / system；system 時依作業系統設定 */
export function resolveDark (mode, systemDark) {
  if (mode === 'dark') return true
  if (mode === 'light') return false
  return !!systemDark
}

/** 讀不到或值不合法時當作跟隨系統（無痕模式等 localStorage 會丟錯） */
export function readMode (storage) {
  try {
    const v = storage?.getItem(THEME_KEY)
    return MODES.includes(v) ? v : 'system'
  } catch {
    return 'system'
  }
}

export function saveMode (storage, mode) {
  try { storage?.setItem(THEME_KEY, mode) } catch { /* 這次有效，下次回到跟隨系統 */ }
}

/** 收支顏色方案：green（綠收紅支，預設）/ red（紅收綠支） */
export function readPnl (storage) {
  try { return storage?.getItem(PNL_KEY) === 'red' ? 'red' : 'green' } catch { return 'green' }
}

export function savePnl (storage, scheme) {
  try { storage?.setItem(PNL_KEY, scheme) } catch { /* 忽略 */ }
}

/** 主題切換按鈕：深色 ↔ 淺色（目前是跟隨系統時，切到與現在相反的那一個） */
export function nextToggleMode (isDark) {
  return isDark ? 'light' : 'dark'
}
