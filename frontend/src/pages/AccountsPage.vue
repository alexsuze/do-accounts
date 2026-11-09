<template>
  <div class="flex flex-col gap-5">
    <PageHeader eyebrow="Accounts" title="帳戶">
      <AppButton @click="startCreate"><Plus :size="16" aria-hidden="true" />新增帳戶</AppButton>
    </PageHeader>

    <div class="flex flex-wrap items-start gap-4">
      <div class="flex min-w-0 flex-[3_1_520px] flex-col gap-3.5">
        <EmptyState v-if="accounts.loaded && !accounts.groups.length && !accounts.archived.length" title="還沒有帳戶" text="先建立常用的錢包、銀行或信用卡帳戶。">
          <AppButton @click="startCreate"><Plus :size="16" aria-hidden="true" />新增帳戶</AppButton>
        </EmptyState>
        <AppCard v-for="g in accounts.groups" :key="g.value" :padded="false" class="overflow-hidden">
          <div class="flex items-center gap-2.5 border-b border-line bg-panel2 px-5 py-3">
            <span class="lbl mr-auto">{{ g.label }} · {{ g.en }}</span>
            <span class="num text-[13px] text-muted">{{ groupTotal(g) }}</span>
          </div>
          <draggable :model-value="g.items" item-key="id" handle=".drag" :animation="150" @update:model-value="list => reorder(g.value, list)">
            <template #item="{ element: a }">
              <div class="flex flex-wrap items-center gap-3.5 border-b border-line px-5 py-2.5 last:border-b-0" :class="selectedId === a.id && 'bg-accent-soft shadow-[inset_3px_0_0_var(--accent)]'">
                <button type="button" class="drag flex size-8 cursor-grab items-center justify-center text-muted" :aria-label="`拖曳排序 ${a.name}`"><GripVertical :size="16" aria-hidden="true" /></button>
                <button type="button" class="flex min-w-0 flex-[1_1_180px] flex-col items-start gap-0.5 text-left text-text" @click="select(a)">
                  <span class="font-semibold">{{ a.name }}</span>
                  <span class="num text-xs text-muted">開戶 {{ a.opening_date }} · 初始 {{ formatAmount(a.opening_balance, a.currency) }}{{ a.visible ? '' : ' · 總覽隱藏' }}</span>
                </button>
                <span class="num rounded-md border border-line px-2 py-0.5 text-xs text-muted">{{ a.currency }}</span>
                <RouterLink :to="`/accounts/${a.id}`" class="flex min-w-[110px] flex-col items-end no-underline">
                  <Amount :value="a.balance" :currency="a.currency" sign="auto" :plain="Number(a.balance) >= 0" class="text-[15px] font-semibold" />
                  <span class="text-[11px] text-muted">查看明細 →</span>
                </RouterLink>
                <SwitchField :model-value="a.visible" :label="`在總覽顯示 ${a.name}`" @update:model-value="v => accounts.setVisible(a, v)" />
              </div>
            </template>
          </draggable>
        </AppCard>

        <AppCard v-if="accounts.archived.length" :padded="false">
          <button type="button" class="flex min-h-12 w-full items-center gap-2.5 px-5 text-left text-muted" :aria-expanded="showArchived" @click="showArchived = !showArchived">
            <Archive :size="16" aria-hidden="true" />
            <span class="mr-auto">已封存的帳戶（{{ accounts.archived.length }}）· 保留在歷史紀錄，不出現在記帳選單</span>
            <span>{{ showArchived ? '收合 ▴' : '顯示 ▾' }}</span>
          </button>
          <div v-if="showArchived">
            <button v-for="a in accounts.archived" :key="a.id" type="button" class="flex min-h-12 w-full items-center gap-3 border-t border-line px-5 text-left text-text opacity-70" @click="select(a)">
              <span class="mr-auto">{{ a.name }}</span><Amount :value="a.balance" :currency="a.currency" sign="auto" plain />
            </button>
          </div>
        </AppCard>
      </div>

      <AppCard v-if="mode" glow class="flex min-w-0 flex-[2_1_380px] flex-col gap-4 lg:sticky lg:top-6">
        <div class="flex items-center gap-2.5">
          <h2 class="m-0 mr-auto font-head text-lg font-semibold">{{ mode === 'create' ? '新增帳戶' : '編輯帳戶' }}</h2>
          <RouterLink v-if="selected" :to="`/accounts/${selected.id}`" class="text-[13px]">查看詳細 →</RouterLink>
        </div>
        <AccountForm :account="selected" :saving="saving" @submit="save" @cancel="close" @archive="toggleArchive" @delete="confirmDelete = true" />
      </AppCard>
    </div>

    <ConfirmDialog v-model:open="confirmDelete" danger :title="`刪除帳戶「${selected?.name}」？`" description="這個帳戶沒有任何交易，刪除後無法復原。" confirm-text="刪除" @confirm="remove" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import { Archive, GripVertical, Plus } from 'lucide-vue-next'
import AccountForm from '../components/AccountForm.vue'
import Amount from '../components/ui/Amount.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import ConfirmDialog from '../components/ui/ConfirmDialog.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import SwitchField from '../components/ui/SwitchField.vue'
import { useAccountsStore } from '../stores/accounts'
import { useUiStore } from '../stores/ui'
import { formatAmount } from '../utils/money'

// 帳戶管理（SA FR-2）：依類型分組、拖曳排序、顯示開關、右側編輯面板（?edit=<id> 或 ?new=1）
const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const accounts = useAccountsStore()
const saving = ref(false)
const showArchived = ref(false)
const confirmDelete = ref(false)

const mode = computed(() => route.query.new ? 'create' : route.query.edit ? 'edit' : null)
const selectedId = computed(() => route.query.edit || null)
const selected = computed(() => selectedId.value ? accounts.byId(selectedId.value) : null)

const groupTotal = (g) => {
  const cur = g.items[0]?.currency
  if (g.items.some(a => a.currency !== cur)) return '多幣別'
  return formatAmount(g.items.reduce((s, a) => s + Number(a.balance), 0), cur)
}

function select (a) { router.replace({ query: { edit: a.id } }) }
function startCreate () { router.replace({ query: { new: '1' } }) }
function close () { router.replace({ query: {} }) }

async function save (body) {
  saving.value = true
  try {
    if (mode.value === 'create') {
      const a = await accounts.create(body)
      router.replace({ query: { edit: a.id } })
    } else {
      await accounts.update(selected.value.id, body)
    }
    ui.ledgerChanged()
  } finally { saving.value = false }
}

async function toggleArchive () {
  await accounts.setArchived(selected.value.id, !selected.value.archived_at)
}
async function remove () {
  await accounts.remove(selected.value.id)
  close()
}

async function reorder (kind, list) {
  const ids = list.map(a => a.id)
  // 先在畫面上換好順序，再送出（失敗會由 api.js 顯示錯誤並重新載入）
  list.forEach((a, i) => { a.order = i })
  accounts.list.sort((a, b) => a.kind === b.kind ? a.order - b.order : 0)
  try { await accounts.reorder(kind, ids) } catch { await accounts.load() }
}

onMounted(() => accounts.load())
watch(() => ui.ledgerVersion, () => accounts.load())
</script>
