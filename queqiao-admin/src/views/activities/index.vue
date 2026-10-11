<script setup lang="ts">
/**
 * 活动管理 — KPI + Hero 精选活动 + 筛选 tabs + 活动卡网格 + 右侧最近动态
 * 数据全部来自后端 /api/activities，发起/编辑/复盘均真实写回。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Activity } from '@/api'
import {
  fetchActivities,
  fetchActivityStats,
  fetchActivity,
  createActivity,
  updateActivity,
  activityCheckin,
  fetchActivityCheckins,
  activityMutual,
  fetchActivityMatches,
} from '@/api'
import ActivityHero from './components/ActivityHero.vue'
import ActivityCard from './components/ActivityCard.vue'

const loading = ref(false)
const activities = ref<Activity[]>([])
const stats = ref<any>({})

const view = ref<'card' | 'list'>('card')
const activeTab = ref('全部')
const tabs = ['全部', '报名中', '进行中', '已结束', '草稿']
const month = ref('')
const sort = ref('按日期排序')

const kpis = computed(() => [
  { label: '进行中活动', value: String(stats.value.ongoing ?? 0), unit: '场', trend: '实时', tone: 'gold', up: true },
  { label: '累计报名', value: String(stats.value.signed ?? 0), unit: '人', trend: '实时', tone: 'success', up: true },
  { label: '活动收入', value: '¥' + (stats.value.revenue ?? 0).toLocaleString(), unit: '', trend: '实时', tone: 'burgundy', up: true },
  { label: '累计活动', value: String(stats.value.total ?? 0), unit: '场', trend: '全部', tone: 'gold', up: true },
])

const hero = computed(() => activities.value.find((a) => a.hero))
const grid = computed(() => {
  let list = activities.value.filter(
    (a) => !a.hero && (activeTab.value === '全部' || a.status === activeTab.value),
  )
  const mm = month.value.replace('月', '')
  if (mm) list = list.filter((a) => (a.date || '').split('.')[1] === mm)
  list = [...list].sort((x, y) =>
    sort.value === '按报名数' ? y.signed - x.signed : (y.date || '').localeCompare(x.date || ''),
  )
  return list
})

const recent = computed(() =>
  [...activities.value]
    .sort((a, b) => (b.date || '').localeCompare(a.date || ''))
    .slice(0, 5)
    .map((a) => ({ text: `${a.title} · ${a.status}`, time: a.date, color: 'var(--dt-primary)' })),
)

const load = async () => {
  loading.value = true
  try {
    const [list, s] = await Promise.all([fetchActivities(), fetchActivityStats()])
    activities.value = list
    stats.value = s
  } catch (e: any) {
    ElMessage.error('加载活动失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

/* 新建 / 编辑 */
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formId = ref('')
const emptyForm = () => ({
  title: '', date: '', time: '', place: '', status: '草稿',
  capacity: 30, price: 0, expectedPairs: 8, agent: '', hero: false, description: '', tags: '',
})
const form = ref(emptyForm())

const openCreate = () => {
  formMode.value = 'create'
  formId.value = ''
  form.value = emptyForm()
  formVisible.value = true
}
const openEdit = (a?: Activity) => {
  const t = a ?? hero.value
  if (!t) return
  formMode.value = 'edit'
  formId.value = t.id
  form.value = {
    title: t.title, date: t.date, time: t.time || '', place: t.place || '', status: t.status || '草稿',
    capacity: t.capacity || 30, price: t.price || 0, expectedPairs: t.expectedPairs || 8, agent: t.agent || '',
    hero: !!t.hero, description: (t as any).description || '', tags: (t.tags || []).join(','),
  }
  formVisible.value = true
}
const saveForm = async () => {
  if (!form.value.title.trim()) { ElMessage.warning('请填写活动标题'); return }
  const payload = {
    ...form.value,
    tags: form.value.tags.split(/[,，]/).map((s) => s.trim()).filter(Boolean),
  }
  try {
    if (formMode.value === 'create') await createActivity(payload)
    else await updateActivity(formId.value, payload)
    ElMessage.success('活动已保存')
    formVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? e))
  }
}

/* 复盘 / 详情 */
const reviewVisible = ref(false)
const reviewActivity = ref<Activity | null>(null)
const reviewSignups = ref<any[]>([])
const checkinList = ref<any[]>([])
const mutualList = ref<any[]>([])
const checkinPick = ref('')
const onsiteA = ref('')
const onsiteB = ref('')

const openReview = async (a?: Activity) => {
  const t = a ?? hero.value
  if (!t) return
  reviewActivity.value = t
  try {
    const d: any = await fetchActivity(t.id)
    reviewSignups.value = d.signups ?? []
  } catch {
    reviewSignups.value = []
  }
  try { checkinList.value = await fetchActivityCheckins(t.id) } catch { checkinList.value = [] }
  try { mutualList.value = await fetchActivityMatches(t.id) } catch { mutualList.value = [] }
  reviewVisible.value = true
}

const checkinMember = async (memberId: string) => {
  if (!reviewActivity.value || !memberId) return
  try {
    const r: any = await activityCheckin(reviewActivity.value.id, memberId)
    if (r.dup) ElMessage.info('该会员已签到')
    else ElMessage.success('签到成功')
    checkinList.value = await fetchActivityCheckins(reviewActivity.value.id)
    checkinPick.value = ''
  } catch (e: any) {
    ElMessage.error('签到失败：' + (e?.message ?? e))
  }
}

const markMutual = async () => {
  if (!reviewActivity.value) return
  const a = onsiteA.value, b = onsiteB.value
  if (!a || !b || a === b) { ElMessage.warning('请选择两位不同的会员'); return }
  try {
    const r: any = await activityMutual(reviewActivity.value.id, a, b)
    if (r.dup) ElMessage.info('已记录该互选')
    else if (r.mutual) ElMessage.success('双向互选成功，已生成牵线候选 ' + r.pairId)
    else ElMessage.success('已记录互选方向')
    mutualList.value = await fetchActivityMatches(reviewActivity.value.id)
    onsiteA.value = ''; onsiteB.value = ''
  } catch (e: any) {
    ElMessage.error('互选失败：' + (e?.message ?? e))
  }
}

const copyCode = async () => {
  if (!reviewActivity.value) return
  const code = 'CHECKIN:' + reviewActivity.value.id
  try { await navigator.clipboard.writeText(code); ElMessage.success('签到码已复制') }
  catch { ElMessage.info(code) }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="activities-page">
    <div class="top-row">
      <div class="view-toggle glass-card">
        <span :class="{ on: view === 'card' }" @click="view = 'card'"><app-icon name="grid" :size="16" /></span>
        <span :class="{ on: view === 'list' }" @click="view = 'list'"><app-icon name="list" :size="16" /></span>
      </div>
      <div style="flex:1" />
      <button class="btn btn-gold" @click="openCreate"><app-icon name="plus" :size="15" />发起活动</button>
    </div>

    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-info">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}<span v-if="k.unit" class="unit">{{ k.unit }}</span></div>
          <div class="kpi-trend" :class="k.up ? 'up' : 'down'">{{ k.trend }}</div>
        </div>
        <div class="kpi-icon" :class="k.tone"><app-icon name="calendar" :size="22" /></div>
      </div>
    </div>

    <div class="act-body">
      <div class="act-main">
        <activity-hero
          v-if="hero"
          :activity="hero"
          @detail="openReview"
          @edit="openEdit"
        />

        <div class="filter-row">
          <div class="chip-group">
            <span v-for="t in tabs" :key="t" class="chip" :class="{ active: activeTab === t }" @click="activeTab = t">{{ t }}</span>
          </div>
          <div style="flex:1" />
          <el-select v-model="month" size="small" clearable placeholder="月份" style="width:90px">
            <el-option v-for="m in ['1月','2月','3月','4月','5月','6月','7月','8月','9月','10月','11月','12月']" :key="m" :label="m" :value="m" />
          </el-select>
          <el-select v-model="sort" size="small" style="width:120px">
            <el-option label="按日期排序" value="按日期排序" />
            <el-option label="按报名数" value="按报名数" />
          </el-select>
        </div>

        <div class="act-grid">
          <activity-card
            v-for="a in grid"
            :key="a.id"
            :activity="a"
            @manage="openEdit"
            @review="openReview"
            @edit="openEdit"
          />
        </div>
      </div>

      <aside class="act-side">
        <div class="glass-card side-card">
          <div class="section-title" style="margin-bottom:16px"><span class="section-title-bar" />最近活动动态</div>
          <div class="feed">
            <div v-for="(r, i) in recent" :key="i" class="feed-item">
              <span class="feed-dot" :style="{ background: r.color }" />
              <div class="feed-text">{{ r.text }}</div>
              <div class="feed-time">{{ r.time }}</div>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '发起活动' : '编辑活动'" width="560px">
      <div class="act-form">
        <label>活动标题</label>
        <el-input v-model="form.title" placeholder="如 「金秋良缘」高端相亲酒会" />
        <div class="form-row">
          <div>
            <label>日期（yyyy.MM.dd）</label>
            <el-input v-model="form.date" placeholder="2026.08.20" />
          </div>
          <div>
            <label>时间</label>
            <el-input v-model="form.time" placeholder="14:00" />
          </div>
        </div>
        <label>地点</label>
        <el-input v-model="form.place" placeholder="如 北京·国贸大酒店" />
        <div class="form-row">
          <div>
            <label>状态</label>
            <el-select v-model="form.status" style="width:100%">
              <el-option label="草稿" value="草稿" />
              <el-option label="报名中" value="报名中" />
              <el-option label="进行中" value="进行中" />
              <el-option label="已满员" value="已满员" />
              <el-option label="已结束" value="已结束" />
            </el-select>
          </div>
          <div>
            <label>负责红娘</label>
            <el-input v-model="form.agent" placeholder="红娘姓名" />
          </div>
        </div>
        <div class="form-row">
          <div>
            <label>名额</label>
            <el-input-number v-model="form.capacity" :min="0" style="width:100%" />
          </div>
          <div>
            <label>单价（元）</label>
            <el-input-number v-model="form.price" :min="0" :step="50" style="width:100%" />
          </div>
          <div>
            <label>预期对数</label>
            <el-input-number v-model="form.expectedPairs" :min="0" style="width:100%" />
          </div>
        </div>
        <label>标签（逗号分隔）</label>
        <el-input v-model="form.tags" placeholder="高端VIP, 酒会, 限50人" />
        <label>活动介绍</label>
        <el-input v-model="form.description" type="textarea" :rows="3" />
        <label class="form-check">
          <el-checkbox v-model="form.hero" /> 设为首页精选（Hero）
        </label>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="formVisible = false">取消</button>
        <button class="btn btn-gold" @click="saveForm">保存</button>
      </template>
    </el-dialog>

    <!-- 复盘弹窗 -->
    <el-dialog v-model="reviewVisible" title="活动复盘" width="560px">
      <template v-if="reviewActivity">
        <div class="review-head">
          <div class="review-title">{{ reviewActivity.title }}</div>
          <div class="review-sub">{{ reviewActivity.date }} · {{ reviewActivity.place }} · 报名 {{ reviewActivity.signed }}/{{ reviewActivity.capacity }}</div>
        </div>
        <div class="review-stats">
          <div><b>{{ reviewActivity.signed }}</b>报名</div>
          <div><b>{{ reviewActivity.female }}</b>女</div>
          <div><b>{{ reviewActivity.male }}</b>男</div>
          <div><b>¥{{ (reviewActivity.revenue ?? 0).toLocaleString() }}</b>收入</div>
          <div><b>{{ reviewActivity.expectedPairs }}</b>预期对</div>
        </div>
        <div class="section-title" style="margin:16px 0 10px"><span class="section-title-bar" />报名名单（{{ reviewSignups.length }}）</div>
        <el-table :data="reviewSignups" size="small" max-height="260">
          <el-table-column prop="memberName" label="会员" />
          <el-table-column prop="gender" label="性别" width="70" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="amount" label="金额" width="90" />
          <el-table-column prop="phone" label="电话" />
        </el-table>

        <!-- 1-E 现场签到码 -->
        <div class="section-title" style="margin:18px 0 10px"><span class="section-title-bar" />现场签到码</div>
        <div class="onsite-code">
          <code>CHECKIN:{{ reviewActivity.id }}</code>
          <button class="btn btn-outline btn-sm" @click="copyCode">复制</button>
        </div>

        <!-- 1-E 现场签到 -->
        <div class="section-title" style="margin:18px 0 10px"><span class="section-title-bar" />现场签到（{{ checkinList.length }}）</div>
        <div class="onsite-row">
          <el-select v-model="checkinPick" size="small" placeholder="选择报名会员签到" style="width:210px" filterable>
            <el-option v-for="s in reviewSignups" :key="s.memberId" :label="s.memberName" :value="s.memberId" />
          </el-select>
          <button class="btn btn-gold btn-sm" @click="checkinMember(checkinPick)">标记签到</button>
        </div>
        <el-table v-if="checkinList.length" :data="checkinList" size="small" max-height="160" style="margin-top:10px">
          <el-table-column prop="memberName" label="会员" />
          <el-table-column label="签到时间">
            <template #default="{ row }">{{ String(row.at ?? '').replace('T', ' ').slice(5, 16) }}</template>
          </el-table-column>
          <el-table-column prop="scannedBy" label="核销人" width="110" />
        </el-table>

        <!-- 1-E 现场互选 -->
        <div class="section-title" style="margin:18px 0 10px"><span class="section-title-bar" />现场互选（{{ mutualList.length }}）</div>
        <div class="onsite-row">
          <el-select v-model="onsiteA" size="small" placeholder="会员 A" style="width:170px" filterable>
            <el-option v-for="s in reviewSignups" :key="s.memberId" :label="s.memberName" :value="s.memberId" />
          </el-select>
          <span class="onsite-x">×</span>
          <el-select v-model="onsiteB" size="small" placeholder="会员 B" style="width:170px" filterable>
            <el-option v-for="s in reviewSignups" :key="s.memberId" :label="s.memberName" :value="s.memberId" />
          </el-select>
          <button class="btn btn-primary btn-sm" @click="markMutual">标记互选</button>
        </div>
        <el-table v-if="mutualList.length" :data="mutualList" size="small" max-height="160" style="margin-top:10px">
          <el-table-column prop="aName" label="A" />
          <el-table-column prop="bName" label="B" />
          <el-table-column label="互选" width="90">
            <template #default="{ row }">
              <span :class="row.mutual ? 'tag-mutual' : 'tag-one'">{{ row.mutual ? '双向✓' : '单向' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="牵线候选" width="120">
            <template #default="{ row }">{{ row.mutual ? (row.pairId || '已建') : '—' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.activities-page { display: flex; flex-direction: column; gap: 20px; }
.top-row { display: flex; align-items: center; }
.view-toggle { display: flex; gap: 4px; padding: 4px; border-radius: var(--dt-radius-lg); }
.view-toggle span { width: 32px; height: 30px; display: flex; align-items: center; justify-content: center; border-radius: var(--dt-radius-md); color: var(--dt-ink-4); cursor: pointer; }
.view-toggle span.on { background: var(--dt-card-solid); color: var(--dt-primary); box-shadow: var(--dt-shadow-sm); }

.act-body { display: flex; gap: 20px; align-items: flex-start; }
.act-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 18px; }

.filter-row { display: flex; align-items: center; gap: 10px; }
.chip-group { display: flex; gap: 6px; }
.chip { font-size: 0.75rem; padding: 4px 14px; border-radius: var(--dt-radius-sm); cursor: pointer; color: var(--dt-ink-2); background: var(--dt-muted); transition: all 0.15s; }
.chip.active { background: var(--dt-primary); color: #fff; font-weight: 600; }

.act-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }

.act-side { width: 320px; flex-shrink: 0; }
.side-card { padding: 20px; }
.feed { display: flex; flex-direction: column; }
.feed-item { display: flex; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--dt-border); }
.feed-item:last-child { border-bottom: none; }
.feed-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 6px; flex-shrink: 0; }
.feed-text { flex: 1; font-size: 0.8125rem; color: var(--dt-ink-2); }
.feed-time { font-size: 0.6875rem; color: var(--dt-ink-4); white-space: nowrap; }

/* 表单 */
.act-form label { display: block; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-2); margin: 12px 0 6px; }
.act-form label:first-child { margin-top: 0; }
.form-row { display: flex; gap: 12px; }
.form-row > div { flex: 1; min-width: 0; }
.form-check { display: flex; align-items: center; gap: 8px; }

.review-head { margin-bottom: 14px; }
.review-title { font-size: 1rem; font-weight: 600; color: var(--dt-ink-1); }
.review-sub { font-size: 0.75rem; color: var(--dt-ink-3); margin-top: 4px; }
.review-stats { display: flex; gap: 16px; flex-wrap: wrap; }
.review-stats div { font-size: 0.75rem; color: var(--dt-ink-3); }
.review-stats b { display: block; font-size: 1.125rem; color: var(--dt-gold-dark); }

/* 1-E 现场签到 + 互选 */
.onsite-code { display: flex; align-items: center; gap: 10px; }
.onsite-code code { background: var(--dt-muted); padding: 6px 12px; border-radius: var(--dt-radius-sm); font-size: 0.8125rem; color: var(--dt-ink-1); letter-spacing: 0.02em; }
.onsite-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.onsite-x { color: var(--dt-ink-4); font-weight: 600; }
.tag-mutual { color: var(--dt-success); font-weight: 600; background: var(--dt-success-soft); padding: 2px 8px; border-radius: var(--dt-radius-sm); font-size: 0.75rem; }
.tag-one { color: var(--dt-ink-3); background: var(--dt-muted); padding: 2px 8px; border-radius: var(--dt-radius-sm); font-size: 0.75rem; }
</style>
