// 下拉選單選項（帳戶、分類）
import { ACCOUNT_KINDS } from '../utils/labels'
import { formatAmount } from '../utils/money'

/** 帳戶選項，依類型分組；只列未封存（目前選中的封存帳戶仍列出，避免編輯舊交易時消失） */
export function accountOptions (accounts, { keep = [], showBalance = true } = {}) {
  return ACCOUNT_KINDS.map(k => ({
    label: k.label,
    options: accounts
      .filter(a => a.kind === k.value && (!a.archived_at || keep.includes(a.id)))
      .map(a => ({
        value: a.id,
        label: `${a.name} · ${a.currency}${showBalance ? ' · ' + formatAmount(a.balance, a.currency) : ''}${a.archived_at ? '（已封存）' : ''}`
      }))
  })).filter(g => g.options.length)
}

/** 分類選項：大類與子類都可選，子類縮排並顯示「大類 › 子類」 */
export function categoryOptions (tree) {
  return tree.map(p => ({
    label: p.name,
    options: [{ value: p.id, label: p.name }, ...p.children.map(c => ({ value: c.id, label: `${p.name} › ${c.name}`, indent: true }))]
  }))
}
