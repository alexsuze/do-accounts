import { defineStore } from 'pinia'
import api from '../services/api'
import { toast } from './toast'

// 收支分類（兩層），前端組成樹狀（SD 3.5、5.2）
export const useCategoriesStore = defineStore('categories', {
  state: () => ({ list: [], loaded: false }),
  getters: {
    byId: (s) => (id) => s.list.find(c => c.id === id),
    /** 某類型的樹：[{ ...大類, children: [...] }]，依 order 排序 */
    tree: (s) => (kind) => s.list
      .filter(c => c.kind === kind && !c.parent_id)
      .sort((a, b) => a.order - b.order)
      .map(p => ({ ...p, children: s.list.filter(c => c.parent_id === p.id).sort((a, b) => a.order - b.order) })),
    /** 「餐飲 › 午餐」 */
    path: (s) => (id) => {
      const c = s.list.find(x => x.id === id)
      if (!c) return '（已刪除的分類）'
      const p = c.parent_id ? s.list.find(x => x.id === c.parent_id) : null
      return p ? `${p.name} › ${c.name}` : c.name
    },
    /** 取得大類（自己就是大類時回傳自己），用來決定圖示與顏色 */
    root: (s) => (id) => {
      const c = s.list.find(x => x.id === id)
      return c?.parent_id ? s.list.find(x => x.id === c.parent_id) ?? c : c
    }
  },
  actions: {
    async load () {
      this.list = (await api.get('/categories')).data
      this.loaded = true
    },
    async ensure () {
      if (!this.loaded) await this.load()
    },
    async create (body) {
      const { data } = await api.post('/categories', body)
      this.list.push(data)
      toast.success(`已新增分類「${data.name}」`)
      return data
    },
    async update (id, body) {
      const { data } = await api.put(`/categories/${id}`, body)
      this.list.splice(this.list.findIndex(c => c.id === id), 1, data)
      toast.success(`已更新分類「${data.name}」`)
      return data
    },
    /** moveTo：有交易時要搬去的分類 */
    async remove (id, moveTo) {
      const name = this.byId(id)?.name
      await api.delete(`/categories/${id}`, { params: moveTo ? { move_to: moveTo } : {} })
      await this.load()
      toast.success(`已刪除分類「${name}」`)
    },
    async reorder (ids) {
      this.list = (await api.put('/categories/reorder', { ids })).data
    }
  }
})
