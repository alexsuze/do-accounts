// 分類圖示代碼 → lucide 元件（後端只存代碼，SD 2.4）
import {
  Book, Briefcase, Bus, Car, Coffee, Ellipsis, Gamepad2, Gift, HeartPulse, House, PiggyBank, Plane, Receipt,
  Scale, Shield, Shirt, ShoppingBag, Smartphone, TrendingUp, Utensils, Wallet, Zap, Baby, PawPrint
} from 'lucide-vue-next'

export const ICONS = {
  utensils: Utensils,
  coffee: Coffee,
  bus: Bus,
  car: Car,
  plane: Plane,
  home: House,
  zap: Zap,
  smartphone: Smartphone,
  'shopping-bag': ShoppingBag,
  shirt: Shirt,
  gamepad: Gamepad2,
  'heart-pulse': HeartPulse,
  book: Book,
  baby: Baby,
  'paw-print': PawPrint,
  shield: Shield,
  receipt: Receipt,
  scale: Scale,
  briefcase: Briefcase,
  gift: Gift,
  'piggy-bank': PiggyBank,
  'trending-up': TrendingUp,
  wallet: Wallet,
  ellipsis: Ellipsis
}

export const ICON_KEYS = Object.keys(ICONS)
export const iconOf = (key) => ICONS[key] ?? Ellipsis
