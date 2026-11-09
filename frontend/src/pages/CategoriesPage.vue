<template>
  <div class="flex flex-col gap-5">
    <PageHeader eyebrow="Categories" title="分類">
      <SegmentedControl :model-value="kind" :options="kinds" label="分類類型" @update:model-value="k => router.replace({ query: { kind: k } })" />
      <AppButton @click="startCreate(null)"><Plus :size="16" aria-hidden="true" />新增分類</AppButton>
    </PageHeader>

    <div class="flex flex-wrap items-start gap-4">
      <AppCard :padded="false" class="min-w-0 flex-[3_1_520px] overflow-hidden">
        <div class="flex items-center border-b border-line bg-panel2 px-5 py-3"><span class="lbl mr-auto">大類 › 子類 · 拖曳可排序</span></div>
        <draggable :model-value="tree" item-key="id" handle=".drag-p" :animation="150" @update:model-value="list => reorder(list)">
          <template #item="{ element: p }">
            <div class="border-b border-line last:border-b-0">
              <div :class="rowClass(p)">
                <button type="button" class="drag-p flex size-8 cursor-grab items-center justify-center text-muted" :aria-label="`拖曳排序 ${p.name}`"><GripVertical :size="16" aria-hidden="true" /></button>
                <CategoryIcon :icon="p.icon" :color="p.color" />
                <button type="button" class="flex min-h-11 min-w-0 flex-1 items-center gap-2.5 text-left text-text" @click="select(p)">
                  <span class="font-semibold">{{ p.name }}</span>
                  <Badge v-if="p.system_key">系統</Badge>
                  <span v-if="p.children.length" class="num text-xs text-muted">{{ p.children.length }} 個子類</span>
                </button>
                <AppButton v-if="!p.system_key" size="sm" variant="plain" :aria-label="`在「${p.name}」新增子類`" @click="startCreate(p)"><Plus :size="14" aria-hidden="true" />子類</AppButton>
              </div>
              <draggable v-if="p.children.length" :model-value="p.children" item-key="id" handle=".drag-c" :animation="150" @update:model-value="list => reorder(list)">
                <template #item="{ element: c }">
                  <div :class="[rowClass(c), 'border-t border-line bg-panel2 pl-14']">
                    <button type="button" class="drag-c flex size-8 cursor-grab items-center justify-center text-muted" :aria-label="`拖曳排序 ${c.name}`"><GripVertical :size="14" aria-hidden="true" /></button>
                    <button type="button" class="flex min-h-11 min-w-0 flex-1 items-center text-left text-sm text-muted hover:text-text" @click="select(c)">└ {{ c.name }}</button>
                  </div>
                </template>
              </draggable>
            </div>
          </template>
        </draggable>
      </AppCard>

      <AppCard v-if="mode" glow class="flex min-w-0 flex-[2_1_360px] flex-col gap-4 lg:sticky lg:top-6">
        <h2 class="m-0 font-head text-lg font-semibold">{{ mode === 'create' ? '新增分類' : '編輯分類' }}</h2>
        <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
          <TextField v-model="f.name" label="名稱" :maxlength="30" :error="err" required />
          <SelectField v-model="f.parent_id" label="上層分類" :options="parentOpts" none-label="（無，為大類）" placeholder="（無，為大類）"
            :disabled="!!selected?.system_key || hasChildren" :hint="hasChildren ? '這個分類底下還有子類，不能變成子類' : '只能有兩層；收入／支出類型不能變更'" />
          <template v-if="!f.parent_id">
            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
              <legend class="lbl mb-2">圖示</legend>
              <div class="grid grid-cols-8 gap-1.5">
                <button v-for="k in ICON_KEYS" :key="k" type="button" :aria-label="`圖示 ${k}`" :aria-pressed="f.icon === k"
                  class="flex h-10 items-center justify-center rounded-lg border" :class="f.icon === k ? 'border-accent bg-accent-soft text-accent' : 'border-line bg-panel2 text-muted'"
                  @click="f.icon = k"><component :is="iconOf(k)" :size="16" aria-hidden="true" /></button>
              </div>
            </fieldset>
            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
              <legend class="lbl mb-2">顏色</legend>
              <div class="flex flex-wrap gap-2">
                <button v-for="c in CATEGORY_COLORS" :key="c" type="button" :aria-label="`顏色 ${c}`" :aria-pressed="f.color === c"
                  class="size-9 rounded-full border-2" :style="{ background: c, borderColor: f.color === c ? 'var(--text)' : 'transparent' }" @click="f.color = c" />
              </div>
            </fieldset>
          </template>
          <p v-else class="m-0 text-xs text-muted">子類沿用大類的圖示與顏色。</p>
          <div class="flex flex-wrap gap-2.5 border-t border-line pt-4">
            <AppButton v-if="selected" variant="danger" :disabled="!!selected.system_key" :title="selected.system_key ? '系統分類不能刪除' : ''" @click="askDelete">刪除</AppButton>
            <span class="mr-auto" />
            <AppButton variant="ghost" @click="close">取消</AppButton>
            <AppButton type="submit" :loading="saving">{{ mode === 'create' ? '新增' : '儲存' }}</AppButton>
          </div>
        </form>
      </AppCard>
    </div>

    <AppDialog v-model:open="deleteOpen" :title="`刪除「${selected?.name}」`" :width="460"
      :description="needMove ? needMove : hasChildren ? '這個分類沒有交易，刪除時子類也會一起刪除，無法復原。' : '這個分類沒有交易，刪除後無法復原。'">
      <SelectField v-if="needMove" v-model="moveTo" label="把交易移到" :options="moveOpts" required />
      <template #footer>
        <AppButton variant="ghost" class="ml-auto" @click="deleteOpen = false">取消</AppButton>
        <AppButton variant="danger" :disabled="!!needMove && !moveTo" :loading="saving" @click="remove">{{ needMove ? '移動並刪除' : '刪除' }}</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import { GripVertical, Plus } from 'lucide-vue-next'
import CategoryIcon from '../components/CategoryIcon.vue'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import AppDialog from '../components/ui/AppDialog.vue'
import Badge from '../components/ui/Badge.vue'
import PageHeader from '../components/ui/PageHeader.vue'
import SegmentedControl from '../components/ui/SegmentedControl.vue'
import SelectField from '../components/ui/SelectField.vue'
import TextField from '../components/ui/TextField.vue'
import api from '../services/api'
import { useCategoriesStore } from '../stores/categories'
import { CATEGORY_COLORS } from '../utils/labels'
import { ICON_KEYS, iconOf } from '../utils/icons'

// 分類管理（SA FR-4）：收入／支出頁籤、兩層、拖曳排序、圖示與顏色、刪除前搬移交易
const route = useRoute()
const router = useRouter()
const categories = useCategoriesStore()
const kinds = [{ value: 'expense', label: '支出' }, { value: 'income', label: '收入' }]
const kind = computed(() => route.query.kind === 'income' ? 'income' : 'expense')
const tree = computed(() => categories.tree(kind.value))
const mode = ref(null)
const selected = ref(null)
const f = reactive({ name: '', parent_id: null, icon: 'ellipsis', color: CATEGORY_COLORS[0] })
const err = ref('')
const saving = ref(false)
const deleteOpen = ref(false)
const needMove = ref('')
const moveTo = ref(null)

const hasChildren = computed(() => !!selected.value && categories.list.some(c => c.parent_id === selected.value.id))
const parentOpts = computed(() => tree.value.filter(p => !p.system_key && p.id !== selected.value?.id).map(p => ({ value: p.id, label: p.name })))
const moveOpts = computed(() => tree.value.flatMap(p => [p, ...p.children.map(c => ({ ...c, name: `${p.name} › ${c.name}` }))])
  .filter(c => c.id !== selected.value?.id && c.parent_id !== selected.value?.id).map(c => ({ value: c.id, label: c.name })))

const rowClass = (c) => ['flex items-center gap-3 px-5 py-1', selected.value?.id === c.id && 'bg-accent-soft shadow-[inset_3px_0_0_var(--accent)]']

function select (c) {
  mode.value = 'edit'
  selected.value = categories.byId(c.id)
  Object.assign(f, { name: c.name, parent_id: c.parent_id ?? null, icon: c.icon || 'ellipsis', color: c.color })
  err.value = ''
}
function startCreate (parent) {
  mode.value = 'create'
  selected.value = null
  Object.assign(f, { name: '', parent_id: parent?.id ?? null, icon: 'ellipsis', color: parent?.color ?? CATEGORY_COLORS[0] })
  err.value = ''
}
function close () { mode.value = null; selected.value = null }

async function save () {
  err.value = f.name.trim() ? '' : '請輸入名稱'
  if (err.value) return
  const parent = f.parent_id ? categories.byId(f.parent_id) : null
  const body = { kind: kind.value, parent_id: f.parent_id, name: f.name.trim(), icon: parent ? parent.icon : f.icon, color: parent ? parent.color : f.color }
  saving.value = true
  try {
    const c = mode.value === 'create' ? await categories.create(body) : await categories.update(selected.value.id, body)
    select(c)
  } finally { saving.value = false }
}

async function askDelete () {
  moveTo.value = null
  needMove.value = ''
  // 用明細查詢這個分類（含子類）有幾筆交易，決定要不要先搬移
  const { data } = await api.get('/transactions', { params: { category_id: selected.value.id, size: 1 } })
  if (data.total) needMove.value = `這個分類${hasChildren.value ? '（含子類）' : ''}有 ${data.total} 筆交易，請選擇要移到哪個分類。移動後才會刪除${hasChildren.value ? '，子類也會一起刪除' : ''}。`
  deleteOpen.value = true
}
async function remove () {
  saving.value = true
  try {
    await categories.remove(selected.value.id, moveTo.value)
    deleteOpen.value = false
    close()
  } finally { saving.value = false }
}

async function reorder (list) {
  list.forEach((c, i) => { const x = categories.byId(c.id); if (x) x.order = i })
  try { await categories.reorder(list.map(c => c.id)) } catch { await categories.load() }
}

onMounted(() => categories.load())
watch(kind, close)
</script>
