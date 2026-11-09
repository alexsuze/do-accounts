import { defineStore } from 'pinia'
import api from '../services/api'
import { toast } from './toast'
import { MAX_ROWS, buildBatch, fromTransaction, newRow, revertRow, rowStatus, summarize } from '../utils/batch'
import { matchName, parseAmount, parseDate, parseKind } from '../utils/paste'
import { today } from '../utils/date'

// 表格模式的狀態（SD 4.2.1）：列、錯誤、草稿、送出
const DRAFT_KEY = 'do-accounts:batch-draft'
const LAST_ACCOUNT = 'do-accounts:last-account'

export const useTransactionBatchStore = defineStore('transactionBatch', {
  state: () => ({
    rows: [],
    /** 載入來源（網址查詢字串）；草稿只在同一個來源才還原 */
    source: '',
    sourceLabel: '',
    truncated: 0,
    /** { [rowKey]: { [field]: msg } }：前端檢查、後端 422、貼上比對不到 */
    errors: {},
    conflicts: [],
    saving: false,
    draftSavedAt: null,
    loading: false
  }),
  getters: {
    summary: (s) => summarize(s.rows),
    changeCount () { const x = this.summary; return x.new + x.modified + x.deleted },
    errorRows: (s) => Object.keys(s.errors).filter(k => Object.keys(s.errors[k]).length).length
  },
  actions: {
    defaults (prev) {
      let last = null
      try { last = localStorage.getItem(LAST_ACCOUNT) } catch { /* 忽略 */ }
      return prev
        ? { date: prev.date, kind: prev.kind, account_id: prev.account_id }
        : { date: today(), kind: 'expense', account_id: last }
    },

    /** query 為空：空白表格；否則依明細的篩選條件載入既有交易（最多 100 筆） */
    async load (query, label) {
      this.loading = true
      this.source = query
      this.sourceLabel = label
      this.errors = {}
      this.conflicts = []
      this.truncated = 0
      try {
        if (!query) {
          this.rows = [newRow(this.defaults())]
          for (let i = 0; i < 2; i++) this.rows.push(newRow(this.defaults(this.rows.at(-1))))
        } else {
          const { data } = await api.get(`/transactions?${query}`, { params: { size: MAX_ROWS, page: 0 } })
          const editable = data.items.filter(t => t.kind !== 'investment')
          this.rows = editable.map(fromTransaction)
          this.truncated = Math.max(0, data.total - data.items.length)
          this.rows.push(newRow(this.defaults(this.rows.at(-1))))
        }
      } finally {
        this.loading = false
      }
    },

    // ---- 編輯 ----
    set (key, field, value) {
      const r = this.rows.find(x => x.key === key)
      if (!r) return
      r[field] = value
      if (field === 'kind' && value !== 'transfer') { r.to_account_id = null; r.to_amount = '' }
      if (field === 'kind' && value === 'transfer') r.category_id = null
      this.clearError(key, field)
    },
    clearError (key, field) {
      if (this.errors[key]) { delete this.errors[key][field]; if (!Object.keys(this.errors[key]).length) delete this.errors[key] }
      this.conflicts = this.conflicts.filter(k => k !== key)
    },
    addRow (afterIndex = this.rows.length - 1) {
      if (this.rows.length >= MAX_ROWS + 50) return toast.warn('表格列數太多，請先儲存一部分')
      const row = newRow(this.defaults(this.rows[afterIndex]))
      this.rows.splice(afterIndex + 1, 0, row)
      return row
    },
    duplicate (index) {
      const src = this.rows[index]
      const copy = newRow({ ...src, key: undefined, id: null, version: null, original: null, deleted: false, hidden: {} })
      this.rows.splice(index + 1, 0, copy)
    },
    /** 既有交易：標記刪除／取消；新列：直接移除 */
    toggleDelete (key) {
      const i = this.rows.findIndex(x => x.key === key)
      const r = this.rows[i]
      if (!r.id) { this.rows.splice(i, 1); delete this.errors[key]; if (!this.rows.length) this.addRow(-1); return }
      r.deleted = !r.deleted
      if (r.deleted) delete this.errors[key]
    },
    revert (key) {
      const i = this.rows.findIndex(x => x.key === key)
      this.rows.splice(i, 1, revertRow(this.rows[i]))
      delete this.errors[key]
    },
    /** Ctrl+D：把上一列同一欄的值往下帶（Excel 的往下填滿） */
    fillDown (index, field) {
      if (index <= 0) return
      this.set(this.rows[index].key, field, this.rows[index - 1][field])
    },

    /**
     * 從 Excel 貼上（SA FR-3.14）。matrix 從 (startIndex, startField) 開始依欄位順序填入，列不夠就新增。
     * ctx：{ columns, categoryItems, accountItems, categoriesById }
     */
    paste (startIndex, startField, matrix, ctx) {
      const startCol = ctx.columns.findIndex(c => c.field === startField)
      if (startCol < 0 || !matrix.length) return 0
      let filled = 0
      matrix.forEach((cells, ri) => {
        let r = this.rows[startIndex + ri]
        if (!r || r.deleted) r = this.addRow(startIndex + ri - 1) ?? null
        if (!r) return
        const err = {}
        const given = new Set()
        let negative = false
        cells.forEach((raw, ci) => {
          const col = ctx.columns[startCol + ci]
          if (!col) return
          const f = col.field
          given.add(f)
          if (f === 'date') { const d = parseDate(raw, today()); if (d) r.date = d; else if (raw) err.date = `無法辨識日期「${raw}」` }
          else if (f === 'kind') { const k = parseKind(raw); if (k) r.kind = k; else if (raw) err.kind = `無法辨識類型「${raw}」` }
          else if (f === 'category_id' || f === 'account_id' || f === 'to_account_id') {
            const id = raw ? matchName(f === 'category_id' ? ctx.categoryItems : ctx.accountItems, raw) : null
            r[f] = id
            if (raw && !id) err[f] = `找不到「${raw}」，請選擇`
          } else if (f === 'amount' || f === 'to_amount') {
            const v = parseAmount(raw)
            if (v === null) err[f] = `無法辨識金額「${raw}」`
            else { if (v.startsWith('-')) negative = true; r[f] = v.replace(/^-/, '') }
          } else r[f] = raw
        })
        // 沒有貼類型時：依分類推斷收入／支出；負數金額視為支出（SD 4.2.1）
        if (!given.has('kind')) {
          const c = ctx.categoriesById[r.category_id]
          if (c) r.kind = c.kind
          else if (negative) r.kind = 'expense'
        }
        this.errors[r.key] = { ...(this.errors[r.key] ?? {}), ...err }
        if (!Object.keys(this.errors[r.key]).length) delete this.errors[r.key]
        filled++
      })
      // 最後留一列空白，方便繼續輸入
      if (rowStatus(this.rows.at(-1)) !== 'blank') this.addRow()
      return filled
    },

    // ---- 送出 ----
    async save (ctx) {
      const b = buildBatch(this.rows, ctx)
      this.conflicts = []
      // 前端檢查有錯：標出來，不送出
      for (const r of this.rows) if (this.errors[r.key] && rowStatus(r) === 'blank') delete this.errors[r.key]
      Object.assign(this.errors, b.errors)
      if (Object.keys(b.errors).length) {
        toast.error(`有 ${Object.keys(b.errors).length} 列資料有誤，請修正後再儲存`)
        return false
      }
      if (!b.count) { toast.info('沒有要儲存的變更'); return false }
      if (b.count > MAX_ROWS) { toast.error(`一次最多儲存 ${MAX_ROWS} 列（目前 ${b.count} 列），請分批儲存`); return false }
      const keyOfRow = (rowNo) => this.rows[rowNo - 1]?.key
      this.saving = true
      try {
        const { data } = await api.post('/transactions/batch', { creates: b.creates, updates: b.updates, deletes: b.deletes }, { silent: true })
        this.clearDraft()
        toast.success(`已新增 ${data.created.length} 筆、修改 ${data.updated.length} 筆、刪除 ${data.deleted_ids.length} 筆`)
        return true
      } catch (e) {
        const body = e.response?.data
        if (e.response?.status === 422 && body?.errors) {
          for (const er of body.errors) {
            const k = keyOfRow(er.row)
            if (k) this.errors[k] = { ...(this.errors[k] ?? {}), [er.field || 'row']: er.message }
          }
        } else if (e.response?.status === 409 && body?.conflicts) {
          this.conflicts = body.conflicts.map(c => keyOfRow(c.row)).filter(Boolean)
        }
        toast.error(body?.detail || e.message)
        return false
      } finally {
        this.saving = false
      }
    },

    // ---- 草稿（SA FR-3.18） ----
    saveDraft () {
      try {
        if (!this.changeCount) { localStorage.removeItem(DRAFT_KEY); this.draftSavedAt = null; return }
        this.draftSavedAt = new Date().toISOString()
        localStorage.setItem(DRAFT_KEY, JSON.stringify({ source: this.source, label: this.sourceLabel, savedAt: this.draftSavedAt, rows: this.rows }))
      } catch { /* 無痕模式等存不了就算了 */ }
    },
    readDraft () {
      try { return JSON.parse(localStorage.getItem(DRAFT_KEY)) } catch { return null }
    },
    restoreDraft (draft) {
      this.rows = draft.rows
      this.source = draft.source
      this.sourceLabel = draft.label
      this.errors = {}
      this.conflicts = []
      this.draftSavedAt = draft.savedAt
    },
    clearDraft () {
      try { localStorage.removeItem(DRAFT_KEY) } catch { /* 忽略 */ }
      this.draftSavedAt = null
    }
  }
})
