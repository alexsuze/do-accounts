<template>
  <div class="flex flex-col gap-5">
    <PageHeader eyebrow="Admin · 只有管理員看得到" title="管理" />

    <div v-if="pendingCount" role="status" class="flex flex-wrap items-center gap-3 rounded-xl border border-warn bg-warn-soft px-4 py-3">
      <Clock :size="18" class="text-warn" aria-hidden="true" />
      <span class="font-semibold text-warn">有 {{ pendingCount }} 個使用申請待審核</span>
      <span class="text-[13px] text-muted">核准後對方就能用 Google 帳號登入；資料各自獨立</span>
    </div>

    <AppCard :padded="false" class="overflow-hidden">
      <div class="flex items-center border-b border-line px-5 py-3.5">
        <h2 class="m-0 mr-auto font-head text-base font-semibold">使用者</h2>
        <span class="num text-xs text-muted">共 {{ users.length }} 人</span>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] border-collapse text-sm">
          <thead>
            <tr class="bg-panel2 text-left">
              <th v-for="h in ['使用者', '狀態', '申請時間', '最後登入']" :key="h" class="lbl border-b border-line px-4 py-2.5 font-medium">{{ h }}</th>
              <th class="lbl border-b border-line px-4 py-2.5 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="5" class="px-4 py-8 text-center text-muted">載入中…</td></tr>
            <tr v-for="u in users" :key="u.id" :class="u.status === 'pending' ? 'bg-warn-soft' : u.status === 'disabled' ? 'opacity-60' : ''">
              <td class="border-b border-line px-4 py-3">
                <span class="flex items-center gap-3">
                  <img v-if="u.picture" :src="u.picture" alt="" class="size-9 rounded-full" referrerpolicy="no-referrer">
                  <span v-else class="flex size-9 items-center justify-center rounded-full border border-line bg-panel2 font-head font-bold">{{ (u.name || u.email).slice(0, 1) }}</span>
                  <span class="flex flex-col gap-0.5"><span class="font-semibold">{{ u.name || '—' }}</span><span class="num text-xs text-muted">{{ u.email }}</span></span>
                </span>
              </td>
              <td class="border-b border-line px-4 py-3"><Badge :tone="u.admin ? 'accent' : tone[u.status]">{{ u.admin ? '管理員' : label[u.status] }}</Badge></td>
              <td class="num border-b border-line px-4 py-3 text-[13px] text-muted">{{ fmt(u.created_at) }}</td>
              <td class="num border-b border-line px-4 py-3 text-[13px] text-muted">{{ fmt(u.last_login_at) }}</td>
              <td class="border-b border-line px-4 py-3">
                <span class="flex justify-end gap-2">
                  <span v-if="u.id === auth.user?.id" class="py-2 text-xs text-muted">（你自己）</span>
                  <template v-else>
                    <AppButton v-if="u.status === 'pending'" size="sm" @click="setStatus(u, 'active')">核准</AppButton>
                    <AppButton v-if="u.status !== 'disabled'" size="sm" variant="danger" @click="askDisable(u)">{{ u.status === 'pending' ? '拒絕' : '停用' }}</AppButton>
                    <AppButton v-if="u.status === 'disabled'" size="sm" variant="ghost" @click="setStatus(u, 'active')">重新啟用</AppButton>
                  </template>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </AppCard>

    <ConfirmDialog v-model:open="confirmOpen" danger :title="`${target?.status === 'pending' ? '拒絕' : '停用'}「${target?.name || target?.email}」？`"
      description="停用後對方會立刻被登出，也無法再登入；資料會保留，之後可以重新啟用。" confirm-text="確定停用"
      @confirm="setStatus(target, 'disabled')" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Clock } from 'lucide-vue-next'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import Badge from '../components/ui/Badge.vue'
import ConfirmDialog from '../components/ui/ConfirmDialog.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import api from '../services/api'
import { useAuthStore } from '../stores/auth'
import { toast } from '../stores/toast'

const auth = useAuthStore()
const users = ref([])
const loading = ref(true)
const confirmOpen = ref(false)
const target = ref(null)
const label = { pending: '待核准', active: '使用中', disabled: '已停用' }
const tone = { pending: 'warn', active: 'inc', disabled: 'muted' }
const pendingCount = computed(() => users.value.filter(u => u.status === 'pending').length)

const df = new Intl.DateTimeFormat('zh-TW', { timeZone: 'Asia/Taipei', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
const fmt = (iso) => iso ? df.format(new Date(iso)) : '—'

async function load () {
  loading.value = true
  try { users.value = (await api.get('/admin/users')).data } finally { loading.value = false }
}

function askDisable (u) {
  target.value = u
  confirmOpen.value = true
}

async function setStatus (u, status) {
  const { data } = await api.put(`/admin/users/${u.id}/status`, { status })
  users.value.splice(users.value.findIndex(x => x.id === u.id), 1, data)
  toast.success(status === 'active' ? `已核准「${data.name || data.email}」` : `已停用「${data.name || data.email}」`)
}

onMounted(load)
</script>
