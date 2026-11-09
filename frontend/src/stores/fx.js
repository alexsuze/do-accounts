import { defineStore } from 'pinia'
import api from '../services/api'

// 匯率（SD 5.2）：最新匯率一律為「1 幣別 = ? TWD」，換算成本位幣時以 TWD 為中介。只用於畫面上的「約當」顯示，正式數字由後端計算
export const useFxStore = defineStore('fx', {
  state: () => ({ latest: [], loaded: false }),
  getters: {
    /** 1 currency = ? TWD；TWD 為 1；沒有資料回 null */
    twd: (s) => (currency) => currency === 'TWD' ? 1 : (Number(s.latest.find(r => r.currency === currency)?.rate) || null),
    /** 最新匯率中最新的日期（側邊欄顯示用） */
    date: (s) => s.latest.reduce((d, r) => (!d || r.date > d ? r.date : d), null)
  },
  actions: {
    async load () {
      this.latest = (await api.get('/fx/latest', { silent: true })).data
      this.loaded = true
    },
    async ensure () {
      if (!this.loaded) await this.load().catch(() => {})
    },
    /** 約當本位幣金額（Number，顯示用）；缺匯率時回 null */
    toBase (amount, currency, base = 'TWD') {
      const a = this.twd(currency)
      const b = this.twd(base)
      return a && b ? Number(amount) * a / b : null
    }
  }
})
