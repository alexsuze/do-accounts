// 顯示用的中文標籤與排序
export const ACCOUNT_KINDS = [
  { value: 'cash', label: '現金', en: 'Cash' },
  { value: 'bank', label: '銀行', en: 'Bank' },
  { value: 'credit_card', label: '信用卡', en: 'Credit' },
  { value: 'e_wallet', label: '電子支付', en: 'E-wallet' },
  { value: 'investment', label: '投資', en: 'Invest' },
  { value: 'loan', label: '貸款', en: 'Loan' },
  { value: 'other', label: '其他', en: 'Other' }
]
export const kindLabel = (k) => ACCOUNT_KINDS.find(x => x.value === k)?.label ?? k

export const TX_KINDS = [
  { value: 'expense', label: '支出' },
  { value: 'income', label: '收入' },
  { value: 'transfer', label: '轉帳' }
]
export const txKindLabel = (k) => ({ expense: '支出', income: '收入', transfer: '轉帳', investment: '投資' })[k] ?? k

export const CURRENCY_OPTIONS = [
  { value: 'TWD', label: 'TWD 新台幣' },
  { value: 'USD', label: 'USD 美元' },
  { value: 'JPY', label: 'JPY 日圓' }
]

export const CATEGORY_COLORS = ['#22D3EE', '#60A5FA', '#A78BFA', '#F472B6', '#FB7185', '#FBBF24', '#34D399', '#64748B']
