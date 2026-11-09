import { defineStore } from 'pinia'
import { nextToggleMode, readMode, readPnl, resolveDark, saveMode, savePnl } from '../utils/theme'

const media = typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)') : null
const storage = typeof window !== 'undefined' ? window.localStorage : undefined

// 介面狀態：主題、收支顏色（SD 5.2、5.7）
export const useUiStore = defineStore('ui', {
  state: () => ({
    mode: readMode(storage),
    pnl: readPnl(storage),
    systemDark: !!media?.matches
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
    setPnl (scheme) {
      this.pnl = scheme
      savePnl(storage, scheme)
      this.apply()
    }
  }
})
