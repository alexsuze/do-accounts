<template>
  <div class="dot-grid flex min-h-screen flex-wrap">
    <section class="flex min-w-0 flex-[1_1_560px] flex-col justify-between gap-9 border-line px-6 py-10 sm:px-14 lg:border-r">
      <AppLogo :size="36" />
      <div class="flex max-w-[560px] flex-col gap-5">
        <span class="lbl">Personal ledger · v2.0</span>
        <h1 class="m-0 font-head text-[40px] leading-tight font-bold sm:text-[52px]">每一筆帳，<br><span class="text-accent">都算得清楚。</span></h1>
        <p class="m-0 text-[17px] leading-relaxed text-muted">多帳戶、多幣別、預算與投資，集中在同一個地方。餘額一律由交易明細算出，不需要再手動同步。</p>
      </div>
      <div class="hidden max-w-[560px] grid-cols-4 gap-2.5 sm:grid">
        <div v-for="f in feats" :key="f.no" class="flex flex-col gap-1 rounded-[10px] border border-line bg-panel p-3">
          <span class="num text-[11px] text-accent">{{ f.no }}</span><span class="text-[13px]">{{ f.label }}</span>
        </div>
      </div>
    </section>

    <section class="flex min-w-0 flex-[1_1_440px] flex-col gap-6 px-6 py-8 sm:px-10">
      <div class="flex justify-end"><ThemeToggle /></div>
      <div class="flex flex-1 items-center justify-center">
        <div class="glow flex w-full max-w-[400px] flex-col gap-5 rounded-[20px] border border-line bg-panel px-8 py-9">
          <div class="flex flex-col gap-2">
            <span class="lbl">{{ pending ? 'Pending approval' : 'Sign in' }}</span>
            <h2 class="m-0 font-head text-[28px] font-bold">{{ pending ? '已送出申請' : '登入' }}</h2>
            <p class="m-0 text-sm leading-relaxed text-muted">
              {{ pending ? '管理員已收到通知，核准後你就能用同一個 Google 帳號登入。' : '使用 Google 帳號登入。第一次登入會送出使用申請，管理員核准後即可使用。' }}
            </p>
          </div>

          <div v-if="message && !pending" role="alert" class="flex items-start gap-2 rounded-[10px] border border-exp bg-exp-soft px-3.5 py-3 text-sm text-exp">
            <CircleAlert :size="18" class="mt-0.5 shrink-0" aria-hidden="true" /><span>{{ message }}</span>
          </div>
          <div v-if="pending" class="flex items-start gap-2 rounded-[10px] border border-warn bg-warn-soft px-3.5 py-3 text-sm">
            <Hourglass :size="18" class="mt-0.5 shrink-0 text-warn" aria-hidden="true" /><span>{{ message }}</span>
          </div>

          <!-- Google 官方按鈕由 Google Identity Services 繪製，樣式依 Google 規範 -->
          <div ref="buttonEl" class="flex min-h-11 justify-center" />
          <div v-if="busy" class="flex justify-center"><span class="size-6 animate-spin rounded-full border-2 border-accent border-t-transparent" aria-label="登入中" /></div>
          <p v-if="!clientId" class="m-0 text-[13px] text-exp">尚未設定 VITE_GOOGLE_CLIENT_ID，無法登入</p>

          <div class="flex flex-col gap-2 border-t border-line pt-4 text-xs text-muted">
            <span class="flex items-center gap-2"><ShieldCheck :size="14" class="text-inc" aria-hidden="true" />系統不保存你的密碼</span>
            <span class="flex items-center gap-2"><Lock :size="14" class="text-inc" aria-hidden="true" />每個人只看得到自己的資料</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleAlert, Hourglass, Lock, ShieldCheck } from 'lucide-vue-next'
import AppLogo from '../components/ui/AppLogo.vue'
import ThemeToggle from '../components/ui/ThemeToggle.vue'
import { safeRedirect } from '../router'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const GSI_SRC = 'https://accounts.google.com/gsi/client'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()
const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID
const buttonEl = ref(null)
const message = ref('')
const pending = ref(false)
const busy = ref(false)
const feats = [['01', '多帳戶多幣別'], ['02', '每日匯率'], ['03', '預算與報表'], ['04', '投資與 CSV']].map(([no, label]) => ({ no, label }))

// Google 登入元件只需要載入一次
function loadGsi () {
  if (window.google?.accounts?.id) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = GSI_SRC
    s.async = true
    s.onload = resolve
    s.onerror = () => reject(new Error('無法載入 Google 登入，請檢查網路後重新整理'))
    document.head.appendChild(s)
  })
}

async function onCredential ({ credential }) {
  busy.value = true
  message.value = ''
  try {
    await auth.login(credential)
    router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    pending.value = e.response?.status === 403 && /核准/.test(e.response?.data?.detail || '')
    message.value = e.response?.data?.detail || e.message
  } finally {
    busy.value = false
  }
}

function renderButton () {
  if (!window.google?.accounts?.id || !buttonEl.value) return
  buttonEl.value.innerHTML = ''
  window.google.accounts.id.renderButton(buttonEl.value, {
    theme: ui.dark ? 'filled_black' : 'outline', size: 'large', shape: 'pill', text: 'signin_with', locale: 'zh-TW'
  })
}

onMounted(async () => {
  if (!clientId) return
  try {
    await loadGsi()
    window.google.accounts.id.initialize({ client_id: clientId, callback: onCredential })
    renderButton()
  } catch (e) {
    message.value = e.message
  }
})

// 切換深淺色時重畫 Google 按鈕
watch(() => ui.dark, renderButton)
</script>
