<script setup lang="ts">
/**
 * 会员档案编辑 — 无侧边栏独立顶栏；左摘要 + 变更时间轴，右三段表单；已修改字段金色标记；底部固定保存栏
 * 数据全部来自后端：/api/members/{id}（详情）、/api/logs?target={id}（变更时间轴）、
 * PUT /api/members/{id} 或 POST /api/members（保存），POST /api/members/parse（AI 智能建档）。
 * 路由带 ?id= 为编辑模式，不带为新增模式。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchMember,
  createMember,
  updateMember,
  parseMemberText,
  fetchAgents,
  fetchLogs,
  fetchRecommendations,
  type Member,
} from '@/api'
import ProfileSummary from './components/ProfileSummary.vue'
import ChangeTimeline from './components/ChangeTimeline.vue'
import BasicInfoForm from './components/BasicInfoForm.vue'
import PreferenceForm from './components/PreferenceForm.vue'
import ServiceInfoForm from './components/ServiceInfoForm.vue'
import { emptyForm, packRequirement, unpackRequirement, type MemberForm, type TimelineEntry } from './types'

const router = useRouter()
const route = useRoute()

/** 子表单引用，用于保存前统一触发 Element Plus 校验 */
const basicFormRef = ref<InstanceType<typeof BasicInfoForm>>()
const svcFormRef = ref<InstanceType<typeof ServiceInfoForm>>()

const memberId = computed(() => String(route.query.id ?? '').trim())
const isEdit = computed(() => !!memberId.value)

const form = reactive<MemberForm>(emptyForm())
/** 加载完成后的原始快照，用于计算「已修改字段」 */
const snapshot = ref<Record<string, unknown>>({})
const tags = ref<string[]>([])
const loading = ref(false)
const saving = ref(false)
const raw = ref<Member | null>(null)
const timeline = ref<TimelineEntry[]>([])
const agentOptions = ref<string[]>([])
const matchCount = ref(0)

/** 表单 → 可比较的普通对象 */
const plain = (f: MemberForm) => ({ ...f, ageRange: f.ageRange.join('-'), heightRange: f.heightRange.join('-') })

const modifiedKeys = computed(() => {
  const cur = plain(form) as Record<string, unknown>
  return Object.keys(cur).filter((k) => JSON.stringify(cur[k]) !== JSON.stringify(snapshot.value[k]))
})
const isModified = (k: string) => modifiedKeys.value.includes(k)
const modifiedCount = computed(() => modifiedKeys.value.length)
/** 兼容子组件的 mark 事件（真正的判定由 diff 完成，此处仅占位） */
const mark = (_k: string) => { /* diff 自动识别，无需手工标记 */ }

const applyMember = (m: Member) => {
  raw.value = m
  form.name = m.name ?? ''
  form.gender = m.gender ?? '男'
  form.age = Number(m.age ?? 28)
  form.height = Number(m.height ?? 170)
  form.education = m.education ?? '本科'
  form.job = m.job ?? ''
  form.income = m.income ?? '30-50万'
  form.city = m.city ?? ''
  form.hometown = m.hometown ?? ''
  form.phone = m.phone ?? ''
  form.marriage = m.marriage ?? '未婚'
  form.hasChild = m.hasChild ?? '无'
  form.house = m.house ?? '暂无'
  form.car = m.car ?? '暂无'
  form.level = m.level ?? '普通'
  form.agent = m.agent ?? ''
  form.status = m.status ?? '待审核'
  form.bio = m.bio ?? ''
  form.birth = m.age ? `${new Date().getFullYear() - Number(m.age)}-01-01` : ''
  unpackRequirement(m.requirement, form)
  tags.value = (m.tags ?? '').split(',').map((s) => s.trim()).filter(Boolean)
  snapshot.value = plain(form) as Record<string, unknown>
}

const loadTimeline = async (id: string) => {
  try {
    const logs: any[] = await fetchLogs(id)
    timeline.value = logs.slice(0, 20).map((l) => ({
      text: l.action ?? '操作',
      detail: l.detail ?? '',
      who: l.userName ? 'agent' : 'system',
      time: (l.createdAt ?? '').replace('T', ' ').slice(5, 16),
      from: '',
      to: '',
    }))
  } catch {
    timeline.value = []
  }
}

onMounted(async () => {
  fetchAgents()
    .then((list: any[]) => { agentOptions.value = list.map((a) => a.name).filter(Boolean) })
    .catch(() => { agentOptions.value = [] })

  if (!isEdit.value) {
    snapshot.value = plain(form) as Record<string, unknown>
    return
  }
  loading.value = true
  try {
    const r: any = await fetchMember(memberId.value)
    applyMember(r?.member ?? r)
    loadTimeline(memberId.value)
    fetchRecommendations(memberId.value, 50)
      .then((rec: any) => { matchCount.value = Number(rec?.total ?? (rec?.list?.length ?? 0)) })
      .catch(() => { matchCount.value = 0 })
  } catch (e: any) {
    ElMessage.error(e?.message || '会员档案加载失败')
  } finally {
    loading.value = false
  }
})

const addTag = (t: string) => {
  const v = t.trim()
  if (v && !tags.value.includes(v)) tags.value.push(v)
}
const removeTag = (t: string) => { tags.value = tags.value.filter((x) => x !== t) }

/** 提交体（与后端 Member 实体字段一一对应） */
const buildPayload = () => ({
  name: form.name.trim(),
  gender: form.gender,
  age: Number(form.age) || null,
  city: form.city.trim(),
  education: form.education,
  job: form.job.trim(),
  level: form.level,
  status: form.status,
  agent: form.agent,
  phone: form.phone.trim(),
  height: Number(form.height) || null,
  income: form.income,
  marriage: form.marriage,
  hasChild: form.hasChild,
  hometown: form.hometown.trim(),
  house: form.house,
  car: form.car,
  requirement: packRequirement(form),
  tags: tags.value.join(','),
  bio: form.bio.trim(),
})

const onSave = async () => {
  if (saving.value) return
  // 保存前统一触发 Element Plus 表单校验（姓名必填 / 手机号格式 / 等级状态必选）
  const tasks = [basicFormRef.value, svcFormRef.value]
    .filter((r): r is NonNullable<typeof r> => !!r)
    .map((r) => r.validate() ?? Promise.resolve())
  try {
    await Promise.all(tasks)
  } catch {
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      const saved = await updateMember(memberId.value, buildPayload())
      applyMember(saved as Member)
      ElMessage.success('档案已保存')
      loadTimeline(memberId.value)
    } else {
      const created: any = await createMember(buildPayload())
      ElMessage.success(`已创建会员 ${created?.id ?? ''}`)
      router.replace(`/members/edit?id=${created?.id}`)
      applyMember(created as Member)
      loadTimeline(created?.id)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const onCancel = async () => {
  if (modifiedCount.value > 0) {
    try {
      await ElMessageBox.confirm(`有 ${modifiedCount.value} 处修改未保存，确定放弃？`, '放弃修改', { type: 'warning' })
    } catch { return }
  }
  router.push('/members')
}

/* AI 智能建档：粘贴一段文本 → 自动填充表单 */
const aiBusy = ref(false)
const onAiParse = async () => {
  try {
    const { value } = await ElMessageBox.prompt(
      '粘贴一段会员自述或聊天记录，AI 将自动提取结构化档案字段',
      'AI 智能建档',
      { inputType: 'textarea', inputPlaceholder: '例：张先生，32岁，硕士，在深圳做产品经理，身高178，年收入60万，未婚有房…', inputPattern: /\S/, inputErrorMessage: '请输入待解析文本' },
    )
    aiBusy.value = true
    const r: any = await parseMemberText(String(value))
    const f = r?.fields ?? r ?? {}
    const setIf = (k: keyof MemberForm, v: any) => { if (v !== undefined && v !== null && v !== '') (form as any)[k] = v }
    setIf('name', f.name); setIf('gender', f.gender); setIf('age', Number(f.age) || undefined)
    setIf('city', f.city); setIf('education', f.education); setIf('job', f.job)
    setIf('height', Number(f.height) || undefined); setIf('income', f.income)
    setIf('marriage', f.marriage ?? f.marital); setIf('house', f.house); setIf('car', f.car)
    setIf('phone', f.phone); setIf('hometown', f.hometown); setIf('bio', f.bio ?? f.intro)
    if (typeof f.requirement === 'string') unpackRequirement(f.requirement, form)
    if (Array.isArray(f.tags)) f.tags.forEach((t: string) => addTag(t))
    else if (typeof f.tags === 'string') f.tags.split(/[,，]/).forEach((t: string) => addTag(t))
    ElMessage.success(`AI 已填充${r?.source ? `（${r.source}）` : ''}，请核对后保存`)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || 'AI 解析失败')
  } finally {
    aiBusy.value = false
  }
}

const headerSub = computed(() => (isEdit.value ? `${form.name || '—'} · ${memberId.value}` : '新增会员档案'))
const summaryMeta = computed(() => `${form.age || '—'}岁 · ${form.gender} · ${memberId.value || '未创建'}`)
const pillType = computed(() =>
  form.status === '已认证' ? 'success' : form.status === '待审核' ? 'warning' : form.status === '已暂停' ? 'neutral' : 'info',
)
</script>

<template>
  <div v-loading="loading" class="edit-page dt-noise">
    <div class="dt-noise-overlay" />
    <!-- 独立顶栏 -->
    <div class="edit-topbar">
      <button class="btn btn-ghost" @click="onCancel"><app-icon name="arrow-left" :size="16" />返回会员库</button>
      <div class="edit-title">
        <div class="edit-title-main">{{ isEdit ? '会员档案编辑' : '新增会员档案' }}</div>
        <div class="edit-title-sub">{{ headerSub }}</div>
      </div>
      <div style="flex:1" />
      <button class="btn btn-outline" :disabled="aiBusy" @click="onAiParse">
        <app-icon name="sparkles" :size="15" />{{ aiBusy ? 'AI 解析中…' : 'AI 智能建档' }}
      </button>
      <span class="level-badge vip">{{ form.level }}</span>
      <StatusPill :type="pillType as any" :text="form.status" />
    </div>

    <div class="edit-body">
      <!-- 左列 -->
      <div class="edit-left">
        <ProfileSummary
          :name="form.name || '新会员'"
          :meta="summaryMeta"
          :level-text="form.level"
          :agent="form.agent || '未分配'"
        />
        <ChangeTimeline v-if="isEdit" :items="timeline" />
      </div>

      <!-- 右列表单 -->
      <div class="edit-right">
        <BasicInfoForm ref="basicFormRef" :form="form" :is-modified="isModified" @mark="mark" />
        <PreferenceForm :form="form" :is-modified="isModified" :match-count="matchCount" @mark="mark" />
        <ServiceInfoForm ref="svcFormRef" :form="form" :tags="tags" :agents="agentOptions" @add-tag="addTag" @remove-tag="removeTag" />
      </div>
    </div>

    <!-- 底部固定保存栏 -->
    <div class="save-bar">
      <button class="btn btn-ghost" @click="onCancel">取消修改</button>
      <div style="flex:1" />
      <span class="save-hint"><b>{{ modifiedCount }}</b> 处字段已修改</span>
      <button class="btn btn-gold" :disabled="saving" @click="onSave">
        <app-icon name="check" :size="15" />{{ saving ? '保存中…' : isEdit ? '保存所有修改' : '创建会员' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.edit-page { min-height: 100vh; background: var(--dt-bg-gradient); display: flex; flex-direction: column; }
.edit-topbar {
  display: flex; align-items: center; gap: 16px; padding: 14px 28px;
  background: var(--dt-glass); backdrop-filter: var(--dt-glass-blur); border-bottom: 1px solid var(--dt-glass-border);
  position: sticky; top: 0; z-index: 10;
}
.edit-title-main { font-size: 1.125rem; font-weight: 700; color: var(--dt-ink-1); }
.edit-title-sub { font-size: 0.75rem; color: var(--dt-primary); }
.edit-body { flex: 1; display: flex; gap: 24px; padding: 24px 28px 120px; align-items: flex-start; }
.edit-left { width: 40%; display: flex; flex-direction: column; gap: 20px; }
.edit-right { width: 60%; display: flex; flex-direction: column; gap: 20px; }

.save-bar {
  position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: 14px;
  padding: 14px 28px; background: var(--dt-glass-strong); backdrop-filter: var(--dt-glass-blur);
  border-top: 1px solid var(--dt-glass-border); z-index: 20;
}
.save-hint { font-size: 0.8125rem; color: var(--dt-ink-3); }
.save-hint b { color: var(--dt-gold-dark); font-size: 1rem; }
</style>
