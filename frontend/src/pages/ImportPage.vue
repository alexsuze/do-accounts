<template>
  <div class="flex flex-col gap-5">
    <PageHeader eyebrow="Import · Export" title="匯入匯出">
      <AppButton variant="ghost" :loading="busy === 'csv'" @click="exportAll"><Download :size="16" aria-hidden="true" />匯出全部 CSV</AppButton>
      <AppButton variant="ghost" :loading="busy === 'backup'" @click="downloadBackup"><Archive :size="16" aria-hidden="true" />下載完整備份</AppButton>
    </PageHeader>

    <ol aria-label="匯入步驟" class="m-0 grid list-none grid-cols-2 gap-2.5 p-0 md:grid-cols-4">
      <li v-for="s in STEPS" :key="s.no">
        <button type="button" :disabled="!canGo(s.no)" :aria-current="step === s.no ? 'step' : undefined"
          class="flex min-h-14 w-full cursor-pointer items-center gap-3 rounded-[12px] border px-3.5 py-2.5 text-left text-text disabled:cursor-not-allowed disabled:opacity-50"
          :class="step === s.no ? 'border-accent bg-accent-soft' : 'border-line bg-panel'" @click="step = s.no">
          <span class="num flex size-[30px] shrink-0 items-center justify-center rounded-full text-[13px] font-semibold"
            :class="step === s.no ? 'bg-accent text-accent-ink' : step > s.no ? 'border border-inc text-inc' : 'border border-line text-muted'">{{ step > s.no ? '✓' : s.no }}</span>
          <span class="flex min-w-0 flex-col"><span class="text-sm font-medium">{{ s.label }}</span><span class="truncate text-xs text-muted">{{ s.sub }}</span></span>
        </button>
      </li>
    </ol>

    <section class="flex flex-wrap items-start gap-4">
      <AppCard :padded="false" class="flex min-w-0 flex-[3_1_620px] flex-col">
        <!-- 1 上傳 -->
        <div v-if="step === 1" class="flex flex-col gap-4 p-5">
          <label class="flex min-h-[220px] cursor-pointer flex-col items-center justify-center gap-2.5 rounded-[14px] border-2 border-dashed px-4 text-center transition-colors"
            :class="dragging ? 'border-accent bg-accent-soft' : 'border-line hover:border-accent'"
            @dragover.prevent="dragging = true" @dragleave="dragging = false" @drop.prevent="onDrop">
            <Upload :size="40" class="text-accent" aria-hidden="true" />
            <span class="font-head text-lg font-semibold">拖曳 CSV 檔案到這裡，或點擊選擇</span>
            <span class="text-sm text-muted">最大 5MB · 支援 UTF-8 與 Big5 · 單次最多 10,000 筆</span>
            <input type="file" accept=".csv,.tsv,.txt,text/csv" class="sr-only" aria-label="選擇 CSV 檔案" @change="onPick">
          </label>
          <div class="flex flex-wrap items-center gap-2">
            <span class="lbl mr-1">先選範本（選填）</span>
            <button v-for="t in allTemplates" :key="t.id" type="button" :aria-pressed="templateId === t.id"
              class="min-h-9 cursor-pointer rounded-lg border px-3 text-[13px]"
              :class="templateId === t.id ? 'border-accent bg-accent-soft text-accent' : 'border-line bg-panel2 text-text'"
              @click="templateId = templateId === t.id ? null : t.id">{{ t.name }}</button>
          </div>
        </div>

        <!-- 2 欄位對應 -->
        <div v-else-if="step === 2 && file" class="flex flex-col gap-4 p-5">
          <div class="flex flex-wrap items-end gap-3">
            <span class="num mr-auto text-[13px] text-muted">{{ file.name }} · {{ file.encoding }}（自動偵測）· {{ body.length }} 列</span>
            <div class="w-full sm:w-64"><SelectField :model-value="templateId" :options="templateOptions" label="套用範本" placeholder="不套用" none-label="不套用"
              @update:model-value="applyTemplate" /></div>
          </div>
          <label class="flex min-h-11 cursor-pointer items-center gap-2.5 text-sm">
            <input v-model="hasHeader" type="checkbox" class="size-[18px] accent-[var(--accent)]">第一列是標題
          </label>
          <div class="overflow-x-auto rounded-[12px] border border-line">
            <table class="w-full min-w-[560px] border-collapse text-sm">
              <thead><tr class="lbl bg-panel2 text-left"><th class="px-3 py-2.5 font-normal">CSV 欄位</th><th class="px-3 py-2.5 font-normal">範例值</th><th class="w-[260px] px-3 py-2.5 font-normal">對應到</th></tr></thead>
              <tbody>
                <tr v-for="(h, i) in headers" :key="i" class="border-t border-line">
                  <td class="num px-3 py-1.5">{{ h }}</td>
                  <td class="num max-w-[240px] truncate px-3 py-1.5 text-muted" :title="sample(i)">{{ sample(i) || '—' }}</td>
                  <td class="px-3 py-1.5"><SelectField v-model="columns[i]" :options="FIELDS" :placeholder="`${h} 對應到`" /></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="grid gap-3 sm:grid-cols-3">
            <SelectField v-model="opts.dateOrder" label="日期格式" :options="DATE_ORDERS" />
            <SelectField v-model="opts.amountRule" label="金額規則" :options="AMOUNT_RULES" />
            <SelectField v-model="opts.defaultAccountId" label="預設帳戶" :options="accountOpts" placeholder="沒有帳戶欄時使用" none-label="（不使用）" />
          </div>
          <div class="flex flex-wrap items-center gap-3">
            <label class="flex min-h-11 cursor-pointer items-center gap-2.5 text-sm">
              <input v-model="saveTpl.on" type="checkbox" class="size-[18px] accent-[var(--accent)]">匯入後把這次的對應存成範本
            </label>
            <div v-if="saveTpl.on" class="w-full sm:w-64"><TextField v-model="saveTpl.name" label="" placeholder="範本名稱，例如：國泰世華對帳單" :maxlength="50" /></div>
          </div>
          <p v-if="mapError" role="alert" class="m-0 text-sm text-exp">{{ mapError }}</p>
        </div>

        <!-- 3 預覽 -->
        <div v-else-if="step === 3 && preview" class="flex flex-col gap-4 p-5">
          <div class="flex flex-wrap gap-2" role="group" aria-label="篩選">
            <button v-for="fl in filters" :key="fl.value" type="button" :aria-pressed="filter === fl.value"
              class="flex h-10 cursor-pointer items-center gap-2 rounded-[10px] border px-3.5 text-sm"
              :class="filter === fl.value ? 'border-accent bg-accent-soft text-accent' : 'border-line bg-panel2 text-text'" @click="filter = fl.value">
              <span>{{ fl.label }}</span><b class="num">{{ fl.count }}</b>
            </button>
          </div>

          <div v-for="m in preview.missing_categories" :key="m.key" class="flex flex-wrap items-center gap-3 rounded-[12px] border border-warn bg-warn-soft px-4 py-2.5">
            <span class="text-sm">找不到{{ m.kind === 'income' ? '收入' : '支出' }}分類「{{ m.name }}」</span>
            <span class="num text-xs text-muted">影響 {{ m.rows }} 列</span>
            <div class="ml-auto w-full sm:w-64"><SelectField :model-value="categoryMap[m.key] ?? null" :options="missingCategoryOptions(m)" :placeholder="`「${m.name}」對應到`"
              @update:model-value="v => setMap(categoryMap, m.key, v)" /></div>
          </div>
          <div v-for="m in preview.missing_accounts" :key="m.name" class="flex flex-wrap items-center gap-3 rounded-[12px] border border-warn bg-warn-soft px-4 py-2.5">
            <span class="text-sm">找不到帳戶「{{ m.name }}」</span>
            <span class="num text-xs text-muted">影響 {{ m.rows }} 列</span>
            <div class="ml-auto w-full sm:w-64"><SelectField :model-value="accountMap[m.name] ?? null" :options="accountOpts" :placeholder="`「${m.name}」對應到`"
              @update:model-value="v => setMap(accountMap, m.name, v)" /></div>
          </div>

          <div class="overflow-x-auto rounded-[12px] border border-line">
            <table class="w-full min-w-[820px] border-collapse text-sm">
              <thead>
                <tr class="lbl bg-panel2 text-left">
                  <th class="w-10 px-3 py-2.5"><span class="sr-only">匯入</span></th>
                  <th class="w-12 px-2 py-2.5 text-right font-normal">列</th><th class="px-3 py-2.5 font-normal">狀態</th><th class="px-3 py-2.5 font-normal">日期</th>
                  <th class="px-3 py-2.5 font-normal">項目</th><th class="px-3 py-2.5 font-normal whitespace-nowrap">分類／帳戶</th><th class="px-3 py-2.5 text-right font-normal">金額</th><th class="min-w-[240px] px-3 py-2.5 font-normal">說明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in shownRows" :key="r.line" class="border-t border-line" :class="r.status === 'error' ? 'bg-exp-soft' : r.status === 'duplicate' && !selected.has(r.line) ? 'opacity-60' : ''">
                  <td class="px-3 py-1"><input type="checkbox" class="size-[18px] accent-[var(--accent)]" :disabled="r.status === 'error'" :checked="selected.has(r.line)"
                    :aria-label="`匯入第 ${r.line} 列`" @change="toggle(r.line)"></td>
                  <td class="num px-2 py-2 text-right text-muted">{{ r.line }}</td>
                  <td class="px-3 py-2"><Badge :tone="STATUS[statusKey(r)].tone">{{ STATUS[statusKey(r)].label }}</Badge></td>
                  <td class="num px-3 py-2 whitespace-nowrap">{{ r.src.date }}</td>
                  <td class="max-w-[200px] truncate px-3 py-2" :title="r.src.title">{{ r.src.title || '—' }}</td>
                  <td class="max-w-[200px] truncate px-3 py-2 text-muted">{{ [r.src.category, r.src.account].filter(Boolean).join(' · ') }}</td>
                  <td class="num px-3 py-2 text-right whitespace-nowrap" :class="r.src.kind === 'income' ? 'text-inc' : r.src.kind === 'expense' ? 'text-exp' : 'text-muted'">
                    {{ r.src.amount == null ? '—' : (r.src.kind === 'income' ? '+' : r.src.kind === 'expense' ? '−' : '') + Number(r.src.amount).toLocaleString('en-US') }}</td>
                  <td class="px-3 py-2 text-xs" :class="r.status === 'error' ? 'text-exp' : 'text-muted'">{{ r.message }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="filteredRows.length > MAX_SHOWN" class="m-0 text-xs text-muted">只顯示前 {{ MAX_SHOWN }} 列（共 {{ filteredRows.length }} 列），全部都會依勾選匯入。</p>
        </div>

        <!-- 4 完成 -->
        <div v-else-if="step === 4 && result" class="flex flex-col items-center gap-3 px-5 py-14 text-center">
          <CircleCheck :size="56" class="text-inc" aria-hidden="true" />
          <span class="font-head text-xl font-semibold">已匯入 {{ result.imported_count }} 筆交易</span>
          <span class="num text-sm text-muted">{{ resultNote }}</span>
          <div class="mt-2 flex flex-wrap justify-center gap-2.5">
            <AppButton variant="ghost" :to="resultLink">查看匯入的交易</AppButton>
            <AppButton @click="reset">再匯入一個檔案</AppButton>
          </div>
        </div>

        <div v-if="step < 4" class="flex flex-wrap items-center gap-3 border-t border-line px-5 py-4">
          <span class="num mr-auto text-xs text-muted">{{ footNote }}</span>
          <AppButton v-if="step > 1" variant="ghost" @click="step--">上一步</AppButton>
          <AppButton v-if="step === 2" :loading="busy === 'preview'" @click="toPreview">下一步：預覽</AppButton>
          <AppButton v-if="step === 3" :loading="busy === 'commit'" :disabled="!selected.size || unresolved" @click="commit">確認匯入 {{ selected.size }} 筆</AppButton>
        </div>
      </AppCard>

      <AppCard class="flex min-w-0 flex-[1_1_280px] flex-col gap-1">
        <h2 class="m-0 mb-2 font-head text-base font-semibold">匯入紀錄</h2>
        <p v-if="!batches.length" class="m-0 text-sm text-muted">還沒有匯入過。</p>
        <div v-for="b in batches" :key="b.id" class="flex flex-col gap-1.5 border-b border-line py-2.5 last:border-b-0">
          <div class="flex items-center gap-2"><span class="num mr-auto truncate text-[13px]" :title="b.file_name">{{ b.file_name }}</span><Badge :tone="BATCH[b.status].tone">{{ BATCH[b.status].label }}</Badge></div>
          <div class="flex items-center gap-2">
            <span class="num mr-auto text-xs text-muted">{{ batchMeta(b) }}</span>
            <AppButton v-if="b.status === 'completed'" variant="danger" @click="askRevert(b)">整批撤銷</AppButton>
          </div>
        </div>
        <span class="pt-2 text-xs text-muted">撤銷會刪除該次匯入的全部交易（含匯入後修改過的）；自動新增的分類會保留。</span>
      </AppCard>
    </section>

    <ConfirmDialog v-model:open="revertOpen" danger :title="`撤銷「${revertTarget?.file_name}」？`" confirm-text="整批撤銷"
      :description="`會刪除這次匯入的 ${revertTarget?.imported_count} 筆交易，無法復原。`" @confirm="revert" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Archive, CircleCheck, Download, Upload } from 'lucide-vue-next'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import Badge from '../components/ui/Badge.vue'
import ConfirmDialog from '../components/ui/ConfirmDialog.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import SelectField from '../components/ui/SelectField.vue'
import TextField from '../components/ui/TextField.vue'
import { accountOptions, categoryOptions } from '../components/options'
import api from '../services/api'
import { download } from '../services/download'
import { useAccountsStore } from '../stores/accounts'
import { useCategoriesStore } from '../stores/categories'
import { useUiStore } from '../stores/ui'
import { toast } from '../stores/toast'
import { decodeBytes, detectDelimiter, parseDelimited } from '../utils/csv'
import { AMOUNT_RULES, BUILTIN_TEMPLATE, DATE_ORDERS, FIELDS, columnsFromTemplate, guessColumns, templateFromColumns, toImportRows } from '../utils/importer'
import { today } from '../utils/date'

// 匯入匯出（SA FR-10、SD 4.7）：上傳 → 欄位對應 → 預覽 → 完成。CSV 在瀏覽器解析，後端檢查與寫入（ADR-17）
const STEPS = [{ no: 1, label: '上傳檔案', sub: '選擇 CSV' }, { no: 2, label: '欄位對應', sub: '套用範本' }, { no: 3, label: '預覽檢查', sub: '重複與錯誤' }, { no: 4, label: '完成', sub: '匯入結果' }]
const STATUS = { ok: { label: '可匯入', tone: 'inc' }, new: { label: '新分類', tone: 'warn' }, duplicate: { label: '重複', tone: 'muted' }, error: { label: '錯誤', tone: 'exp' } }
const BATCH = { completed: { label: '已完成', tone: 'inc' }, reverted: { label: '已撤銷', tone: 'muted' }, failed: { label: '失敗', tone: 'exp' } }
const CREATE = '__create__'
const MAX_BYTES = 5 * 1024 * 1024
const MAX_SHOWN = 300

const accounts = useAccountsStore()
const categories = useCategoriesStore()
const ui = useUiStore()
const step = ref(1)
const busy = ref('')
const dragging = ref(false)
const templates = ref([])
const templateId = ref(null)
const file = ref(null)
const hasHeader = ref(true)
const columns = ref([])
const opts = reactive({ amountRule: 'signed', dateOrder: 'ymd', defaultAccountId: null })
const saveTpl = reactive({ on: false, name: '' })
const mapError = ref('')
const preview = ref(null)
const rows = ref([])
const accountMap = reactive({})
const categoryMap = reactive({})
const selected = reactive(new Set())
const filter = ref('all')
const result = ref(null)
const batches = ref([])
const revertTarget = ref(null)
const revertOpen = ref(false)

const allTemplates = computed(() => [...templates.value, BUILTIN_TEMPLATE])
const templateOptions = computed(() => allTemplates.value.map(t => ({ value: t.id, label: t.name })))
const accountOpts = computed(() => accountOptions(accounts.list, { showBalance: false }))
const headers = computed(() => hasHeader.value ? file.value.table[0] : file.value.table[0].map((_, i) => `第 ${i + 1} 欄`))
const body = computed(() => hasHeader.value ? file.value.table.slice(1) : file.value.table)
const sample = (i) => body.value.find(r => r[i])?.[i] ?? ''

const canGo = (n) => n === 1 || (n === 2 && !!file.value) || (n === 3 && !!preview.value) || (n === 4 && !!result.value)
const missingCategoryOptions = (m) => [{ label: '新增', options: [{ value: CREATE, label: `＋ 新增${m.kind === 'income' ? '收入' : '支出'}分類「${m.name}」` }] }, ...categoryOptions(categories.tree(m.kind))]

// 預覽列：後端的狀態＋送出的資料
const merged = computed(() => {
  const byLine = new Map(rows.value.map(r => [r.line, r]))
  return (preview.value?.rows ?? []).map(r => ({ ...r, src: byLine.get(r.line) ?? {} }))
})
const statusKey = (r) => r.status === 'ok' && r.new_category ? 'new' : r.status
const filteredRows = computed(() => merged.value.filter(r => filter.value === 'all' || r.status === filter.value))
const shownRows = computed(() => filteredRows.value.slice(0, MAX_SHOWN))
const filters = computed(() => [
  { value: 'all', label: '全部', count: preview.value?.total ?? 0 },
  { value: 'ok', label: '可匯入', count: preview.value?.ok ?? 0 },
  { value: 'duplicate', label: '重複', count: preview.value?.duplicate ?? 0 },
  { value: 'error', label: '錯誤', count: preview.value?.error ?? 0 }
])
const unresolved = computed(() => (preview.value?.missing_accounts ?? []).some(m => !accountMap[m.name]) ||
  (preview.value?.missing_categories ?? []).some(m => !categoryMap[m.key]))
const footNote = computed(() => {
  if (step.value === 2 && file.value) return `下一步會檢查全部 ${body.value.length} 列`
  if (step.value === 3 && preview.value) return unresolved.value ? '請先處理上方找不到的帳戶或分類' : `確認後只匯入勾選的 ${selected.size} 筆；可在「匯入紀錄」整批撤銷`
  return ''
})
const resultNote = computed(() => {
  const r = result.value
  const parts = [`略過 ${r.skipped_count} 列`]
  if (r.created_categories?.length) parts.push(`新增分類「${r.created_categories.join('」「')}」`)
  return parts.join(' · ')
})
const resultLink = computed(() => {
  const first = rows.value.filter(r => selected.has(r.line)).map(r => r.date).sort()[0]
  return { path: '/transactions', query: first && /^\d{4}-\d{2}/.test(first) ? { month: first.slice(0, 7) } : {} }
})

function batchMeta (b) {
  const d = b.created_at ? new Date(b.created_at).toLocaleDateString('zh-TW', { month: '2-digit', day: '2-digit', timeZone: 'Asia/Taipei' }) : ''
  if (b.status === 'reverted') return `${d} · 撤銷 ${b.reverted_count} 筆`
  if (b.status === 'failed') return `${d} · 寫入失敗，未匯入`
  return `${d} · 匯入 ${b.imported_count} 筆${b.skipped_count ? `，略過 ${b.skipped_count}` : ''}`
}

// ---------- 1 上傳 ----------
function onPick (e) {
  const f = e.target.files?.[0]
  e.target.value = ''
  if (f) load(f)
}
function onDrop (e) {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) load(f)
}
async function load (f) {
  if (f.size > MAX_BYTES) return toast.error('檔案超過 5MB，請分成多個檔案')
  const { text, encoding } = decodeBytes(new Uint8Array(await f.arrayBuffer()))
  const table = parseDelimited(text, detectDelimiter(text))
  if (table.length < 2) return toast.error('檔案裡沒有資料列')
  if (table.length - 1 > 10000) return toast.error('單次最多匯入 10,000 筆，請分成多個檔案')
  const width = Math.max(...table.map(r => r.length))
  file.value = { name: f.name, encoding, table: table.map(r => [...r, ...Array(width - r.length).fill('')]) }
  hasHeader.value = true
  Object.keys(accountMap).forEach(k => delete accountMap[k])
  Object.keys(categoryMap).forEach(k => delete categoryMap[k])
  preview.value = null
  result.value = null
  applyTemplate(templateId.value)
  step.value = 2
}

// ---------- 2 欄位對應 ----------
function applyTemplate (id) {
  templateId.value = id
  const t = allTemplates.value.find(x => x.id === id)
  if (!file.value) return
  columns.value = t ? columnsFromTemplate(headers.value, t.mapping) : guessColumns(headers.value)
  // 沒有範本時：有收入／支出欄而沒有金額欄，就猜是「分開兩欄」
  if (!t) opts.amountRule = !columns.value.includes('amount') && (columns.value.includes('income') || columns.value.includes('expense')) ? 'split' : 'signed'
  if (t) {
    opts.amountRule = t.mapping.amount_rule ?? 'signed'
    opts.dateOrder = t.mapping.date_order ?? 'ymd'
    opts.defaultAccountId = accounts.list.some(a => a.id === t.mapping.default_account_id) ? t.mapping.default_account_id : null
  }
  saveTpl.on = false
  saveTpl.name = t && t.id !== 'builtin' ? t.name : ''
}

function buildRows () {
  const c = columns.value
  mapError.value = ''
  if (!c.includes('date')) mapError.value = '請指定哪一欄是日期'
  else if (opts.amountRule === 'split' ? !(c.includes('income') || c.includes('expense') || c.includes('amount')) : !c.includes('amount')) {
    mapError.value = opts.amountRule === 'split' ? '請指定收入金額或支出金額欄' : '請指定哪一欄是金額'
  } else if (!c.includes('account') && !opts.defaultAccountId) mapError.value = '沒有帳戶欄時，請選擇預設帳戶'
  if (mapError.value) return null
  return toImportRows(body.value, c, { ...opts, today: today(), firstLine: hasHeader.value ? 2 : 1 })
}

function request (lines) {
  return {
    file_name: file.value.name, rows: rows.value, default_account_id: opts.defaultAccountId,
    account_map: { ...accountMap }, category_map: { ...categoryMap }, lines
  }
}

async function toPreview () {
  const built = buildRows()
  if (!built) return
  rows.value = built
  busy.value = 'preview'
  try {
    await runPreview(true)
    filter.value = 'all'
    step.value = 3
  } finally {
    busy.value = ''
  }
}

// ---------- 3 預覽 ----------
// 勾選狀態：可匯入的列預設勾選、重複預設不勾；記住使用者手動改過的，重新預覽時保留
const unchecked = new Set()
const forced = new Set()
async function runPreview (fresh) {
  if (fresh) { unchecked.clear(); forced.clear() }
  let { data } = await api.post('/import/transactions/preview', request(null))
  // 找不到的分類預設「新增」，再預覽一次讓狀態正確
  const unmapped = data.missing_categories.filter(m => !categoryMap[m.key])
  if (unmapped.length) {
    unmapped.forEach(m => { categoryMap[m.key] = CREATE })
    data = (await api.post('/import/transactions/preview', request(null))).data
  }
  preview.value = data
  selected.clear()
  for (const r of data.rows) {
    if ((r.status === 'ok' && !unchecked.has(r.line)) || (r.status === 'duplicate' && forced.has(r.line))) selected.add(r.line)
  }
}
function toggle (line) {
  if (selected.has(line)) {
    selected.delete(line); unchecked.add(line); forced.delete(line)
  } else {
    selected.add(line); unchecked.delete(line); forced.add(line)
  }
}
async function setMap (map, key, value) {
  if (value) map[key] = value
  else delete map[key]
  busy.value = 'preview'
  try { await runPreview(false) } finally { busy.value = '' }
}

async function commit () {
  busy.value = 'commit'
  try {
    const { data } = await api.post('/import/transactions/commit', request([...selected]))
    result.value = data
    if (saveTpl.on && saveTpl.name.trim()) await saveTemplate().catch(() => {})
    toast.success(`已匯入 ${data.imported_count} 筆交易`)
    ui.ledgerChanged()
    await Promise.all([loadBatches(), categories.load(), accounts.load()])
    step.value = 4
  } finally {
    busy.value = ''
  }
}

async function saveTemplate () {
  const mapping = templateFromColumns(headers.value, columns.value, opts)
  const existing = templates.value.find(t => t.id === templateId.value && t.name === saveTpl.name.trim())
  const body_ = { name: saveTpl.name.trim(), mapping }
  if (existing) await api.put(`/import/templates/${existing.id}`, body_)
  else await api.post('/import/templates', body_)
  templates.value = (await api.get('/import/templates')).data
  toast.success(`已儲存範本「${body_.name}」`)
}

function reset () {
  file.value = null
  preview.value = null
  result.value = null
  rows.value = []
  selected.clear()
  step.value = 1
}

// ---------- 紀錄、匯出 ----------
async function loadBatches () {
  batches.value = (await api.get('/import/batches')).data
}
function askRevert (b) {
  revertTarget.value = b
  revertOpen.value = true
}
async function revert () {
  const b = revertTarget.value
  if (!b) return
  const { data } = await api.post(`/import/batches/${b.id}/revert`)
  toast.success(`已撤銷「${b.file_name}」，刪除 ${data.reverted_count} 筆交易`)
  ui.ledgerChanged()
  await Promise.all([loadBatches(), accounts.load()])
}
async function exportAll () {
  busy.value = 'csv'
  try { await download('/export/transactions', `do-accounts-全部交易-${today()}.csv`) } finally { busy.value = '' }
}
async function downloadBackup () {
  busy.value = 'backup'
  try { await download('/export/backup', `do-accounts-backup-${today()}.json`) } finally { busy.value = '' }
}

onMounted(async () => {
  await Promise.all([accounts.ensure(), categories.ensure(), loadBatches(),
    api.get('/import/templates').then(r => { templates.value = r.data })])
})
</script>
