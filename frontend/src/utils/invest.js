// 投資計算（前端預設值與即時預覽，SD 4.5）。實際成本與損益由後端計算；這裡只負責帶入台股的手續費與證交稅
import Decimal from 'decimal.js-light'

export const TRADE_TYPES = [
  { value: 'buy', label: '買進' },
  { value: 'sell', label: '賣出' },
  { value: 'cash_dividend', label: '現金股利' },
  { value: 'stock_dividend', label: '股票股利' },
  { value: 'split', label: '分割' },
  { value: 'fee', label: '費用' },
  { value: 'opening', label: '期初持股' }
]
export const tradeLabel = (v) => TRADE_TYPES.find(t => t.value === v)?.label ?? v

export const MARKETS = [
  { value: 'TW', label: '台股上市' }, { value: 'TWO', label: '台股上櫃' }, { value: 'US', label: '美股' },
  { value: 'FUND', label: '基金' }, { value: 'CRYPTO', label: '加密貨幣' }, { value: 'OTHER', label: '其他' }
]
export const ASSET_TYPES = [
  { value: 'stock', label: '個股' }, { value: 'etf', label: 'ETF' }, { value: 'fund', label: '基金' },
  { value: 'bond', label: '債券' }, { value: 'crypto', label: '加密貨幣' }, { value: 'other', label: '其他' }
]
export const marketLabel = (v) => MARKETS.find(m => m.value === v)?.label ?? v
export const assetTypeLabel = (v) => ASSET_TYPES.find(m => m.value === v)?.label ?? v

/** 自動報價來源：台股上市、上櫃 */
export const hasAutoQuote = (market) => market === 'TW' || market === 'TWO'
const isTw = hasAutoQuote

/**
 * 台股手續費：成交金額 × 0.1425% × 折扣，無條件捨去，最低 20 元。其他市場回 0（自行輸入）
 * @param discount 例如 0.6（6 折）；1 為無折扣
 */
export function twFee (gross, market, discount = 1) {
  if (!isTw(market) || !(gross > 0)) return 0
  const fee = new Decimal(gross).times('0.001425').times(discount).toDecimalPlaces(0, Decimal.ROUND_DOWN).toNumber()
  return Math.max(20, fee)
}

/** 台股賣出證交稅：個股 0.3%、ETF 0.1%，無條件捨去。其他市場回 0 */
export function twTax (gross, market, assetType) {
  if (!isTw(market) || !(gross > 0)) return 0
  const rate = assetType === 'etf' ? '0.001' : '0.003'
  return new Decimal(gross).times(rate).toDecimalPlaces(0, Decimal.ROUND_DOWN).toNumber()
}

const d = (v) => {
  try { return new Decimal(String(v ?? '').replace(/,/g, '') || 0) } catch { return new Decimal(0) }
}

/**
 * 交割金額（證券幣別，流入為正）與成交金額；沒有現金的類型回 null
 */
export function settlement (type, { quantity, price, fee, tax }) {
  const q = d(quantity); const p = d(price); const f = d(fee); const t = d(tax)
  const gross = q.times(p)
  switch (type) {
    case 'buy': return { gross: gross.toNumber(), cash: gross.plus(f).plus(t).negated().toNumber() }
    case 'sell': return { gross: gross.toNumber(), cash: gross.minus(f).minus(t).toNumber() }
    case 'cash_dividend': return { gross: gross.toNumber(), cash: gross.minus(f).minus(t).toNumber() }
    case 'fee': return { gross: 0, cash: f.negated().toNumber() }
    default: return null
  }
}

/**
 * 交易後的持股與均價（預覽用，移動平均成本法）
 * @param holding { quantity, cost }（目前持股；沒有時為 0）
 */
export function afterTrade (type, holding, { quantity, price, fee, tax }) {
  const held = d(holding?.quantity); const cost = d(holding?.cost)
  const q = d(quantity)
  const avg = (c, h) => h.isZero() ? 0 : c.div(h).toNumber()
  switch (type) {
    case 'buy': {
      const c = cost.plus(q.times(d(price))).plus(d(fee)).plus(d(tax)); const h = held.plus(q)
      return { quantity: h.toNumber(), avg: avg(c, h) }
    }
    case 'sell': {
      const h = held.minus(q)
      const realized = q.times(d(price)).minus(d(fee)).minus(d(tax)).minus(q.times(avg(cost, held)))
      return { quantity: h.toNumber(), avg: avg(cost, held), realized: realized.toNumber(), oversell: h.isNegative() }
    }
    case 'stock_dividend': {
      const h = held.plus(q)
      return { quantity: h.toNumber(), avg: avg(cost, h) }
    }
    case 'split': {
      const h = held.times(q)
      return { quantity: h.toNumber(), avg: avg(cost, h) }
    }
    case 'fee': return { quantity: held.toNumber(), avg: avg(cost.plus(d(fee)), held) }
    case 'opening': {
      const c = cost.plus(q.times(d(price))); const h = held.plus(q)
      return { quantity: h.toNumber(), avg: avg(c, h) }
    }
    default: return { quantity: held.toNumber(), avg: avg(cost, held) }
  }
}
