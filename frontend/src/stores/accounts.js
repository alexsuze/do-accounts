import { defineStore } from 'pinia'
import api from '../services/api'
import { toast } from './toast'
import { ACCOUNT_KINDS } from '../utils/labels'

// 帳戶清單（含後端算好的餘額，SD 5.2）
export const useAccountsStore = defineStore('accounts', {
  state: () => ({ list: [], loaded: false }),
  getters: {
    byId: (s) => (id) => s.list.find(a => a.id === id),
    active: (s) => s.list.filter(a => !a.archived_at),
    /** 依類型分組（只含未封存），給總覽與帳戶頁用 */
    groups: (s) => ACCOUNT_KINDS
      .map(k => ({ ...k, items: s.list.filter(a => a.kind === k.value && !a.archived_at) }))
      .filter(g => g.items.length),
    archived: (s) => s.list.filter(a => a.archived_at)
  },
  actions: {
    async load () {
      this.list = (await api.get('/accounts', { params: { include_archived: true } })).data
      this.loaded = true
    },
    async ensure () {
      if (!this.loaded) await this.load()
    },
    async create (body) {
      const { data } = await api.post('/accounts', body)
      this.list.push(data)
      toast.success(`已新增帳戶「${data.name}」`)
      return data
    },
    async update (id, body) {
      const { data } = await api.put(`/accounts/${id}`, body)
      this.replace(data)
      toast.success(`已更新帳戶「${data.name}」`)
      return data
    },
    async remove (id) {
      const name = this.byId(id)?.name
      await api.delete(`/accounts/${id}`)
      this.list = this.list.filter(a => a.id !== id)
      toast.success(`已刪除帳戶「${name}」`)
    },
    async setArchived (id, archived) {
      const { data } = await api.post(`/accounts/${id}/${archived ? 'archive' : 'unarchive'}`)
      this.replace(data)
      toast.success(archived ? `已封存「${data.name}」` : `已取消封存「${data.name}」`)
    },
    /** 顯示開關：樂觀更新，失敗回滾（SD 5.2） */
    async setVisible (a, visible) {
      const old = a.visible
      a.visible = visible
      try {
        await api.put(`/accounts/${a.id}`, toRequest({ ...a, visible }))
      } catch (e) {
        a.visible = old
        throw e
      }
    },
    async reorder (kind, ids) {
      const { data } = await api.put('/accounts/reorder', { kind, ids })
      this.list = data
    },
    replace (data) {
      const i = this.list.findIndex(a => a.id === data.id)
      if (i >= 0) this.list.splice(i, 1, data)
      else this.list.push(data)
    }
  }
})

/** 帳戶物件 → PUT body */
export function toRequest (a) {
  return {
    name: a.name,
    kind: a.kind,
    currency: a.currency,
    opening_balance: a.opening_balance,
    opening_date: a.opening_date,
    visible: a.visible,
    note: a.note,
    credit: a.kind === 'credit_card' ? (a.credit ?? null) : null
  }
}
