<template>
  <div class="flex flex-col gap-6">
    <PageHeader eyebrow="Settings" title="設定" />

    <div class="flex flex-wrap items-start gap-4">
      <div class="flex min-w-0 flex-[1_1_460px] flex-col gap-4">
        <AppCard class="flex flex-wrap items-center gap-4">
          <img v-if="auth.user?.picture" :src="auth.user.picture" alt="" class="size-14 rounded-full" referrerpolicy="no-referrer">
          <span v-else class="flex size-14 items-center justify-center rounded-full bg-accent font-head text-[22px] font-bold text-accent-ink">{{ initial }}</span>
          <span class="mr-auto flex min-w-0 flex-col gap-1">
            <span class="text-[17px] font-semibold">{{ auth.user?.name }}</span>
            <span class="num truncate text-[13px] text-muted">{{ auth.user?.email }} · Google 帳號</span>
            <Badge v-if="auth.isAdmin" tone="accent" class="self-start">管理員</Badge>
          </span>
          <AppButton variant="danger" @click="logout"><LogOut :size="16" aria-hidden="true" />登出</AppButton>
        </AppCard>

        <AppCard>
          <h2 class="m-0 mb-1 font-head text-base font-semibold">外觀</h2>
          <div class="flex flex-wrap items-center gap-3 border-b border-line py-3.5">
            <span class="mr-auto">主題</span>
            <SegmentedControl :model-value="ui.mode" :options="modes" label="主題" @update:model-value="ui.setMode" />
          </div>
          <div class="flex flex-wrap items-center gap-3 py-3.5">
            <span class="mr-auto flex flex-col gap-1">
              <span>收支顏色</span>
              <span class="num text-[13px]"><span class="text-inc">收入 +1,200</span>　<span class="text-exp">支出 −350</span></span>
            </span>
            <SegmentedControl :model-value="ui.pnl" :options="pnls" label="收支顏色" @update:model-value="ui.setPnl" />
          </div>
        </AppCard>
      </div>

      <div class="flex min-w-0 flex-[1_1_360px] flex-col gap-4">
        <AppCard>
          <h2 class="m-0 mb-1 font-head text-base font-semibold">記帳偏好</h2>
          <div class="flex flex-wrap items-center gap-3 border-b border-line py-3.5">
            <span class="mr-auto flex flex-col gap-0.5"><span>本位幣</span><span class="text-xs text-muted">總覽、報表、預算都換算成這個幣別</span></span>
            <div class="w-44"><SelectField :model-value="prefs.base_currency" :options="CURRENCY_OPTIONS" label="" placeholder="本位幣" @update:model-value="askBase" /></div>
          </div>
          <div class="flex flex-wrap items-center gap-3 border-b border-line py-3.5">
            <span class="mr-auto">每週起始日</span>
            <SegmentedControl :model-value="prefs.week_start" :options="WEEK" label="每週起始日" @update:model-value="v => save({ week_start: v })" />
          </div>
          <div class="flex flex-wrap items-center gap-3 py-3.5">
            <span class="mr-auto flex flex-col gap-0.5"><span>預算警示門檻</span><span class="text-xs text-muted">達到這個比例時進度條變橘色</span></span>
            <span class="flex items-center gap-2">
              <input v-model="warn" type="number" min="1" max="100" inputmode="numeric" aria-label="預算警示門檻（%）"
                class="num h-11 w-20 rounded-[10px] border border-line bg-panel2 px-3 text-right text-text outline-none focus:border-accent"
                @change="saveWarn">
              <span>%</span>
            </span>
          </div>
        </AppCard>

        <AppCard class="flex flex-col gap-2.5">
          <div class="flex flex-wrap items-center gap-2.5">
            <h2 class="m-0 mr-auto font-head text-base font-semibold">匯率</h2>
            <span class="num text-xs text-muted">每天 06:00、18:00 自動更新</span>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full border-collapse text-sm">
              <thead>
                <tr class="lbl text-left"><th class="py-2 font-normal">幣別</th><th class="py-2 text-right font-normal">1 單位 = TWD</th><th class="py-2 pl-3 font-normal">日期</th><th class="py-2 font-normal max-sm:hidden">來源</th><th /></tr>
              </thead>
              <tbody>
                <tr v-for="r in fx.latest" :key="r.currency" class="border-t border-line">
                  <td class="num py-2 font-semibold">{{ r.currency }}</td>
                  <td class="num py-2 text-right">{{ Number(r.rate).toFixed(4) }}</td>
                  <td class="num py-2 pl-3 whitespace-nowrap">{{ shortDate(r.date) }}</td>
                  <td class="py-2 text-xs max-sm:hidden" :class="r.manual ? 'text-accent' : 'text-muted'">{{ sourceLabel(r) }}</td>
                  <td class="py-1 text-right"><AppButton variant="ghost" @click="openManual(r.currency)">手動覆寫</AppButton></td>
                </tr>
                <tr v-for="r in manual" :key="r.id" class="border-t border-line bg-accent-soft">
                  <td class="num py-2 pl-2 font-semibold">{{ r.currency }}</td>
                  <td class="num py-2 text-right">{{ Number(r.rate).toFixed(4) }}</td>
                  <td class="num py-2 pl-3 whitespace-nowrap">{{ r.date }}</td>
                  <td class="py-2 text-xs text-accent max-sm:hidden">手動（只影響你）</td>
                  <td class="py-1 text-right"><AppButton variant="danger" @click="removeManual(r)">移除</AppButton></td>
                </tr>
              </tbody>
            </table>
          </div>
          <span class="text-xs text-muted">報表用交易當天的匯率換算；同一天有手動值時優先使用手動值。</span>
          <span v-if="seedOnly" class="text-xs text-warn">目前還是內建的初始匯率，排程抓到最新匯率後會自動更新；需要精確數字可以先手動覆寫。</span>
        </AppCard>

        <AppCard>
          <h2 class="m-0 mb-1 font-head text-base font-semibold">通知</h2>
          <div class="flex flex-wrap items-center gap-3 border-b border-line py-3.5">
            <label class="mr-auto flex cursor-pointer flex-col gap-0.5" for="monthly-switch">
              <span>每月報表 Email</span>
              <span class="text-xs text-muted">每月 1 日早上寄上個月的收支、支出前五名、預算與淨資產到 {{ auth.user?.email }}</span>
            </label>
            <SwitchField id="monthly-switch" :model-value="prefs.monthly_report" label="每月報表 Email" @update:model-value="v => save({ monthly_report: v })" />
          </div>
          <div class="flex flex-wrap items-center gap-2.5 py-3.5">
            <AppButton variant="ghost" :loading="busyMail === 'preview'" @click="previewMail"><Eye :size="16" aria-hidden="true" />預覽上個月</AppButton>
            <AppButton variant="ghost" :loading="busyMail === 'test'" @click="testMail"><Send :size="16" aria-hidden="true" />寄測試信給我</AppButton>
          </div>
        </AppCard>

        <AppCard>
          <h2 class="m-0 mb-1 font-head text-base font-semibold">資料與備份</h2>
          <div class="flex flex-wrap items-center gap-3 border-b border-line py-3.5">
            <span class="mr-auto flex flex-col gap-0.5"><span>完整備份</span><span class="text-xs text-muted">帳戶、分類、交易、預算、手動匯率、匯入範本，JSON 檔</span></span>
            <AppButton variant="ghost" :loading="busyBackup" @click="downloadBackup"><Download :size="16" aria-hidden="true" />下載備份</AppButton>
          </div>
          <div class="flex flex-wrap items-center gap-3 border-b border-line py-3.5">
            <span class="mr-auto flex flex-col gap-0.5"><span>從備份還原</span><span class="text-xs text-muted">只能還原到還沒有任何交易的帳號（例如新帳號）</span></span>
            <label class="inline-flex h-11 cursor-pointer items-center gap-2 rounded-[10px] border border-line bg-panel px-4 text-sm hover:bg-panel2">
              <Upload :size="16" aria-hidden="true" />選擇備份檔
              <input type="file" accept=".json,application/json" class="sr-only" aria-label="選擇備份檔" @change="pickBackup">
            </label>
          </div>
          <div class="flex flex-wrap items-center gap-3 py-3.5">
            <span class="mr-auto flex flex-col gap-0.5"><span>CSV 匯入匯出</span><span class="text-xs text-muted">銀行對帳單匯入、匯入紀錄與整批撤銷</span></span>
            <RouterLink to="/import" class="text-sm">前往匯入匯出 →</RouterLink>
          </div>
        </AppCard>
        <AppCard v-if="auth.isAdmin" class="flex flex-wrap items-center gap-3">
          <span class="lbl mr-auto">管理員工具</span>
          <RouterLink to="/admin" class="text-sm">使用者核准 →</RouterLink>
        </AppCard>
      </div>
    </div>

    <AppDialog v-model:open="manualOpen" title="手動覆寫匯率" description="只影響你自己的換算；同一天再設定一次會覆蓋。" :width="440">
      <form id="fx-form" class="flex flex-col gap-4" novalidate @submit.prevent="saveManual">
        <div class="grid gap-3 sm:grid-cols-2">
          <SelectField v-model="mf.currency" label="幣別" :options="FX_OPTIONS" required />
          <DateField v-model="mf.date" label="日期" :max="today()" :error="mErr.date" required />
        </div>
        <TextField v-model="mf.rate" :label="`1 ${mf.currency} = ? TWD`" inputmode="decimal" mono align="right" :error="mErr.rate" required />
      </form>
      <template #footer>
        <AppButton variant="ghost" class="ml-auto" @click="manualOpen = false">取消</AppButton>
        <AppButton type="submit" form="fx-form" :loading="busy">儲存</AppButton>
      </template>
    </AppDialog>
    <AppDialog v-model:open="mailPreview.open" :title="mailPreview.subject || '每月報表預覽'" description="實際寄出的信件內容（信箱程式的顯示可能略有不同）" :width="640">
      <iframe :srcdoc="mailPreview.html" title="每月報表預覽" sandbox="" class="h-[60vh] w-full rounded-[10px] border border-line bg-white" />
    </AppDialog>
    <ConfirmDialog v-model:open="restoreOpen" :title="`從「${restoreFile?.name}」還原？`" confirm-text="還原"
      :description="restoreFile?.summary" @confirm="restore" />
    <ConfirmDialog v-model:open="baseConfirm" :title="`本位幣改成 ${pendingBase}？`" confirm-text="變更"
      description="總覽與報表會改用新的本位幣換算。已設定的預算金額不會換算，請到預算頁確認金額。" @confirm="save({ base_currency: pendingBase })" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Download, Eye, LogOut, Send, Upload } from 'lucide-vue-next'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import AppDialog from '../components/ui/AppDialog.vue'
import Badge from '../components/ui/Badge.vue'
import ConfirmDialog from '../components/ui/ConfirmDialog.vue'
import DateField from '../components/ui/DateField.vue'
import SelectField from '../components/ui/SelectField.vue'
import SwitchField from '../components/ui/SwitchField.vue'
import TextField from '../components/ui/TextField.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import SegmentedControl from '../components/ui/SegmentedControl.vue'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { useFxStore } from '../stores/fx'
import { toast } from '../stores/toast'
import api from '../services/api'
import { download } from '../services/download'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { CURRENCY_OPTIONS } from '../utils/labels'
import { shortDate, today } from '../utils/date'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const modes = [{ value: 'dark', label: '深色' }, { value: 'light', label: '淺色' }, { value: 'system', label: '跟隨系統' }]
const pnls = [{ value: 'green', label: '綠收紅支' }, { value: 'red', label: '紅收綠支' }]
const initial = computed(() => (auth.user?.name || auth.user?.email || '?').slice(0, 1).toUpperCase())

const WEEK = [{ value: 'mon', label: '星期一' }, { value: 'sun', label: '星期日' }]
const FX_OPTIONS = CURRENCY_OPTIONS.filter(o => o.value !== 'TWD')
const SOURCES = { 'currency-api': 'currency-api', 'currency-api-mirror': 'currency-api（備援）', seed: '內建初始值', manual: '手動' }
const fx = useFxStore()
const prefs = computed(() => ({
  base_currency: auth.user?.base_currency || 'TWD',
  week_start: auth.user?.settings?.week_start || 'mon',
  budget_warn_pct: auth.user?.settings?.budget_warn_pct ?? 80,
  monthly_report: !!auth.user?.settings?.monthly_report
}))
const warn = ref(String(prefs.value.budget_warn_pct))
watch(() => prefs.value.budget_warn_pct, v => { warn.value = String(v) })
const baseConfirm = ref(false)
const pendingBase = ref('')
const manual = ref([])
const manualOpen = ref(false)
const busy = ref(false)
const mf = reactive({ currency: 'USD', date: today(), rate: '' })
const mErr = reactive({})
const seedOnly = computed(() => fx.latest.some(r => r.source === 'seed'))
const sourceLabel = (r) => r.manual ? '手動（只影響你）' : SOURCES[r.source] ?? r.source

async function save (body) {
  await auth.updateSettings(body)
  toast.success('已儲存設定')
  if (body.base_currency) await fx.load()
}
function askBase (v) {
  if (!v || v === prefs.value.base_currency) return
  pendingBase.value = v
  baseConfirm.value = true
}
function saveWarn () {
  const n = Number(warn.value)
  if (!Number.isInteger(n) || n < 1 || n > 100) {
    toast.error('預算警示門檻需為 1～100 的整數')
    warn.value = String(prefs.value.budget_warn_pct)
    return
  }
  if (n !== prefs.value.budget_warn_pct) save({ budget_warn_pct: n })
}

async function loadFx () {
  const [, m] = await Promise.all([fx.load(), api.get('/fx/manual')])
  manual.value = m.data
}
function openManual (currency) {
  const latest = fx.latest.find(r => r.currency === currency)
  Object.assign(mf, { currency, date: today(), rate: latest ? String(Number(latest.rate)) : '' })
  Object.keys(mErr).forEach(k => delete mErr[k])
  manualOpen.value = true
}
async function saveManual () {
  Object.keys(mErr).forEach(k => delete mErr[k])
  const rate = String(mf.rate).replace(/,/g, '').trim()
  if (!/^\d+(\.\d{1,8})?$/.test(rate) || !(Number(rate) > 0)) mErr.rate = '請輸入大於 0 的數字（最多 8 位小數）'
  if (!mf.date) mErr.date = '請選擇日期'
  if (Object.keys(mErr).length) return
  busy.value = true
  try {
    await api.put('/fx/manual', { currency: mf.currency, date: mf.date, rate })
    toast.success(`已設定 ${mf.date} 的 ${mf.currency} 匯率`)
    manualOpen.value = false
    await loadFx()
  } finally {
    busy.value = false
  }
}
async function removeManual (r) {
  await api.delete(`/fx/manual/${r.id}`)
  toast.success(`已移除 ${r.date} 的手動 ${r.currency} 匯率`)
  await loadFx()
}
onMounted(loadFx)

// ---------- 每月報表 Email ----------
const busyMail = ref('')
const mailPreview = reactive({ open: false, subject: '', html: '' })
async function previewMail () {
  busyMail.value = 'preview'
  try {
    const { data } = await api.get('/reports/monthly-email/preview')
    Object.assign(mailPreview, { open: true, subject: data.subject, html: data.html })
  } finally {
    busyMail.value = ''
  }
}
async function testMail () {
  busyMail.value = 'test'
  try {
    const { data } = await api.post('/reports/monthly-email/test', null, { timeout: 30000 })
    toast.success(`已寄出 ${data.month} 的報表到 ${data.to}`)
  } finally {
    busyMail.value = ''
  }
}

// ---------- 備份與還原（SA FR-10.8） ----------
const busyBackup = ref(false)
const restoreFile = ref(null)
const restoreOpen = ref(false)
async function downloadBackup () {
  busyBackup.value = true
  try { await download('/export/backup', `do-accounts-backup-${today()}.json`) } finally { busyBackup.value = false }
}
async function pickBackup (e) {
  const f = e.target.files?.[0]
  e.target.value = ''
  if (!f) return
  let data
  try { data = JSON.parse(await f.text()) } catch { return toast.error('檔案不是正確的 JSON') }
  if (data?.format !== 'do-accounts-backup') return toast.error('這不是 do-accounts 的備份檔')
  const n = (k) => (data[k] ?? []).length
  restoreFile.value = {
    name: f.name, data,
    summary: `備份時間 ${String(data.exported_at ?? '').slice(0, 10)}：帳戶 ${n('accounts')} 個、分類 ${n('categories')} 個、交易 ${n('transactions')} 筆、預算 ${n('budgets')} 項。目前的帳戶、分類與預算設定會被取代。`
  }
  restoreOpen.value = true
}
async function restore () {
  const f = restoreFile.value
  if (!f) return
  const { data } = await api.post('/import/backup', f.data, { timeout: 120000 })
  toast.success(`已還原：帳戶 ${data.accounts} 個、交易 ${data.transactions} 筆`)
  await Promise.all([auth.refresh(), useAccountsStore().load(), useCategoriesStore().load(), loadFx()])
  ui.ledgerChanged()
}

function logout () {
  auth.logout()
  router.replace({ name: 'login' })
}
</script>
