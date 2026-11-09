import { createRouter, createWebHashHistory } from 'vue-router'
import { authToken, loadSession } from '../services/session'

const soon = (title, stage) => ({ component: () => import('../pages/ComingSoonPage.vue'), meta: { title, stage } })

// 使用 hash 模式，避免 GitHub Pages 子路徑重新整理 404（SD 5.1）
const routes = [
  { path: '/login', name: 'login', component: () => import('../pages/LoginPage.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('../pages/DashboardPage.vue'), meta: { title: '總覽' } },
      { path: 'transactions', name: 'transactions', component: () => import('../pages/TransactionsPage.vue'), meta: { title: '明細' } },
      { path: 'transactions/grid', name: 'grid', component: () => import('../pages/TransactionGridPage.vue'), meta: { title: '多筆編輯', wide: true } },
      { path: 'accounts', name: 'accounts', component: () => import('../pages/AccountsPage.vue'), meta: { title: '帳戶' } },
      { path: 'accounts/:id', name: 'account', component: () => import('../pages/AccountDetailPage.vue'), meta: { title: '帳戶詳細' } },
      { path: 'categories', name: 'categories', component: () => import('../pages/CategoriesPage.vue'), meta: { title: '分類' } },
      { path: 'budgets', name: 'budgets', component: () => import('../pages/BudgetsPage.vue'), meta: { title: '預算' } },
      { path: 'reports', name: 'reports', component: () => import('../pages/ReportsPage.vue'), meta: { title: '報表' } },
      { path: 'investments', name: 'investments', ...soon('投資', 4) },
      { path: 'import', name: 'import', component: () => import('../pages/ImportPage.vue'), meta: { title: '匯入匯出' } },
      { path: 'settings', name: 'settings', component: () => import('../pages/SettingsPage.vue'), meta: { title: '設定' } },
      { path: 'admin', name: 'admin', component: () => import('../pages/AdminPage.vue'), meta: { title: '管理', admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 沒登入只能看登入頁（記住原本要去哪，登入後帶回去）；已登入就不用再看登入頁；管理頁只給管理員（後端也會擋）
router.beforeEach(to => {
  const loggedIn = !!authToken()
  if (!to.meta.public && !loggedIn) return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  if (to.name === 'login' && loggedIn) return safeRedirect(to.query.redirect)
  if (to.meta.admin && !loadSession().user?.admin) return { name: 'dashboard' }
})

router.afterEach(to => {
  document.title = to.meta.title ? `${to.meta.title} · do-accounts` : 'do-accounts'
})

// 只接受站內路徑，避免被帶到奇怪的地方
export function safeRedirect (path) {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') ? path : { name: 'dashboard' }
}

export const NAV = [
  { no: '01', label: '總覽', to: '/', icon: 'LayoutDashboard' },
  { no: '02', label: '明細', to: '/transactions', icon: 'List' },
  { no: '03', label: '表格編輯', to: '/transactions/grid', icon: 'Table' },
  { no: '04', label: '帳戶', to: '/accounts', icon: 'Wallet' },
  { no: '05', label: '分類', to: '/categories', icon: 'Tags' },
  { no: '06', label: '預算', to: '/budgets', icon: 'Target' },
  { no: '07', label: '報表', to: '/reports', icon: 'ChartColumn' },
  { no: '08', label: '投資', to: '/investments', icon: 'TrendingUp' },
  { no: '09', label: '匯入匯出', to: '/import', icon: 'ArrowDownUp' },
  { no: '10', label: '設定', to: '/settings', icon: 'Settings' }
]

export default router
