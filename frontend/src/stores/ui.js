import { defineStore } from 'pinia'
import { nextToggleMode, readMode, readPnl, resolveDark, saveMode, savePnl } from '../utils/theme'

const media = typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)') : null
const storage = typeof window !== 'undefined' ? window.localStorage : undefined

// 介面狀態：主題、收支顏色（SD 5.2、5.7）
export const useUiStore = defineStore('ui', {
  state: () => ({
    mode: readMode(storage),
    pnl: readPnl(storage),
    systemDark: !!media?.matches,
    /** 交易有異動時 +1，頁面 watch 它來重新載入（記帳視窗在任何頁面都能開） */
    ledgerVersion: 0,
    tradeDialog: null,
    /** 按 / 時設為 true，明細頁的搜尋框取得焦點後改回 false */
    focusSearch: false,
    /** 記帳視窗：null 為關閉；{ transaction?, preset? } 為開啟 */
    txDialog: null,
    /** 全域計算機（SA FR-11.1）：target 為帶入結果的 callback */
    calculator: { open: false, target: null, initial: '' }
  }),
  getters: {
    dark: (s) => resolveDark(s.mode, s.systemDark)
  },
  actions: {
    init () {
      media?.addEventListener('change', e => { this.systemDark = e.matches; this.apply() })
      this.apply()
    },
    apply () {
      const html = document.documentElement
      html.classList.toggle('dark', this.dark)
      html.classList.toggle('flip-pnl', this.pnl === 'red')
    },
    setMode (mode) {
      this.mode = mode
      saveMode(storage, mode)
      this.apply()
    },
    toggle () {
      this.setMode(nextToggleMode(this.dark))
    },
    ledgerChanged () {
      this.ledgerVersion++
    },
    /** 開啟記帳視窗。transaction：要編輯的交易；preset：新增時的預設值（例如帳戶、複製的內容） */
    openTx ({ transaction = null, preset = null } = {}) {
      if (transaction?.kind === 'investment') return this.openTrade({ id: transaction.id })
      this.txDialog = { transaction, preset, key: Date.now() }
    },
    /** 開啟投資交易視窗。id：要編輯的交易；preset：新增時的預設值（帳戶、證券、類型） */
    openTrade ({ id = null, preset = null } = {}) {
      this.tradeDialog = { id, preset, key: Date.now() }
    },
    closeTrade () {
      this.tradeDialog = null
    },
    closeTx () {
      this.txDialog = null
    },
    openCalculator (target = null, initial = '') {
      this.calculator = { open: true, target, initial }
    },
    closeCalculator () {
      this.calculator = { open: false, target: null, initial: '' }
    },
    setPnl (scheme) {
      this.pnl = scheme
      savePnl(storage, scheme)
      this.apply()
    }
  }
})
