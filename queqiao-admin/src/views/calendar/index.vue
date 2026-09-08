<script setup lang="ts">
/**
 * 日程日历 — 顶部视图切换 + KPI + 周视图时间轴日历 + 今日日程/即将到来侧栏
 * 数据来自后端 /api/calendar，新建/编辑/状态变更/删除均真实写回。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { CalEvent } from '@/api'
import {
  fetchCalendar,
  fetchCalendarToday,
  fetchCalendarStats,
  createEvent,
  updateEvent,
  deleteEvent,
  setEventStatus,
} from '@/api'
import CalendarGrid from './components/CalendarGrid.vue'
import CalendarSidePanel from './components/CalendarSidePanel.vue'

const view = ref<'日' | '周' | '月'>('周')
const loading = ref(false)

const cursor = ref(new Date())
const weekStart = computed(() => {
  const d = new Date(cursor.value)
  const day = (d.getDay() + 6) % 7 // 周一=0
  d.setDate(d.getDate() - day)
  d.setHours(0, 0, 0, 0)
  return d
})
const ym = computed(() => ({ year: weekStart.value.getFullYear(), month: weekStart.value.getMonth() + 1 }))

const events = ref<CalEvent[]>([])
const todayEvents = ref<CalEvent[]>([])
const stats = ref<any>({})

const kpis = computed(() => [
  { label: '本月日程', value: String(stats.value.total ?? 0), unit: '项', tone: 'gold' },
  { label: '已确认', value: String(stats.value.confirmed ?? 0), unit: '项', tone: 'success' },
  { label: '待确认', value: String(stats.value.pending ?? 0), unit: '项', tone: 'warning' },
  { label: '今日日程', value: String(todayEvents.value.length), unit: '项', tone: 'burgundy' },
])

const load = async () => {
  loading.value = true
  try {
    const [list, today, st] = await Promise.all([
      fetchCalendar(ym.value),
      fetchCalendarToday(),
      fetchCalendarStats(ym.value),
    ])
    events.value = list
    todayEvents.value = today
    stats.value = st
  } catch (e: any) {
    ElMessage.error('加载日程失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

const weekEvents = computed(() =>
  events.value.filter((e) => {
    if (!e.date) return false
    const d = new Date(e.date + 'T00:00:00')
    return d >= weekStart.value && d < new Date(weekStart.value.getTime() + 7 * 86400000)
  }),
)
const todayStr = new Date().toISOString().slice(0, 10)
const dayList = computed(() => events.value.filter((e) => e.date === todayStr))
const monthList = computed(() =>
  [...events.value].sort((a, b) => (a.date || '').localeCompare(b.date || '')),
)
const upcoming = computed(() =>
  events.value
    .filter((e) => e.date && e.date > todayStr)
    .sort((a, b) => (a.date || '').localeCompare(b.date || ''))
    .slice(0, 3),
)

const goToday = () => (cursor.value = new Date())
const goPrev = () => { const d = new Date(cursor.value); d.setDate(d.getDate() - 7); cursor.value = d }
const goNext = () => { const d = new Date(cursor.value); d.setDate(d.getDate() + 7); cursor.value = d }

/* 新建 / 编辑 */
const formVisible = ref(false)
const formId = ref('')
const emptyForm = () => ({
  title: '', date: todayStr, startTime: '14:00', endTime: '15:00',
  place: '', type: '约见', agent: '', status: '待确认',
})
const form = ref(emptyForm())

const openCreate = () => {
  formId.value = ''
  form.value = { ...emptyForm() }
  formVisible.value = true
}
const openEdit = (ev: CalEvent) => {
  formId.value = ev.id
  form.value = {
    title: ev.title, date: ev.date || todayStr, startTime: ev.startTime || '14:00',
    endTime: ev.endTime || '15:00', place: ev.place || '', type: ev.type || '约见',
    agent: ev.agent || '', status: ev.status || '待确认',
  }
  formVisible.value = true
}
const saveEvent = async () => {
  if (!form.value.title.trim()) { ElMessage.warning('请填写日程标题'); return }
  const payload = { ...form.value }
  try {
    if (formId.value) await updateEvent(formId.value, payload)
    else await createEvent(payload)
    ElMessage.success('日程已保存')
    formVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? e))
  }
}
const onChangeStatus = async (status: string) => {
  if (!formId.value) return
  try {
    await setEventStatus(formId.value, status)
    ElMessage.success('状态已更新：' + status)
    formVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('更新失败：' + (e?.message ?? e))
  }
}
const onDelete = async () => {
  if (!formId.value) return
  try {
    await deleteEvent(formId.value)
    ElMessage.success('日程已删除')
    formVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message ?? e))
  }
}

onMounted(load)
const TYPE_OPTIONS = ['约见', '活动', '会议', '审核·回访', '其他']
</script>

<template>
  <div v-loading="loading" class="cal-page">
    <div class="cal-top">
      <button class="btn btn-outline btn-sm" @click="goToday">今天</button>
      <button class="icon-btn" @click="goPrev"><app-icon name="chevron-left" :size="16" /></button>
      <button class="icon-btn" @click="goNext"><app-icon name="chevron-right" :size="16" /></button>
      <div class="view-toggle">
        <span v-for="v in ['日','周','月']" :key="v" :class="{ on: view === v }" @click="view = v as any">{{ v }}</span>
      </div>
      <div style="flex:1" />
      <button class="btn btn-gold btn-sm" @click="openCreate"><app-icon name="plus" :size="14" />新建日程</button>
    </div>

    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-info">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}<span class="unit">{{ k.unit }}</span></div>
        </div>
        <div class="kpi-icon" :class="k.tone"><app-icon name="calendar-days" :size="22" /></div>
      </div>
    </div>

    <div class="cal-body">
      <calendar-grid
        v-if="view === '周'"
        :events="weekEvents"
        :week-start="weekStart"
        @open="openEdit"
        @add="openCreate"
      />

      <div v-else-if="view === '日'" class="list-pane glass-card">
        <div class="list-title">今日日程（{{ dayList.length }}）</div>
        <div v-for="(e, i) in dayList" :key="i" class="list-item" @click="openEdit(e)">
          <div class="li-time">{{ e.startTime }}–{{ e.endTime }}</div>
          <div class="li-main"><div class="li-title">{{ e.title }}</div><div class="li-sub">{{ e.place || e.type }}</div></div>
          <span class="li-status" :class="{ done: e.status === '已完成' }">{{ e.status }}</span>
        </div>
        <div v-if="!dayList.length" class="side-empty">今日暂无日程</div>
      </div>

      <div v-else class="list-pane glass-card">
        <div class="list-title">本月日程（{{ monthList.length }}）</div>
        <div v-for="(e, i) in monthList" :key="i" class="list-item" @click="openEdit(e)">
          <div class="li-time">{{ (e.date || '').slice(5) }} {{ e.startTime }}</div>
          <div class="li-main"><div class="li-title">{{ e.title }}</div><div class="li-sub">{{ e.place || e.type }}</div></div>
          <span class="li-status" :class="{ done: e.status === '已完成' }">{{ e.status }}</span>
        </div>
        <div v-if="!monthList.length" class="side-empty">本月暂无日程</div>
      </div>

      <calendar-side-panel :today="todayEvents" :upcoming="upcoming" @add="openCreate" @open="openEdit" />
    </div>

    <el-dialog v-model="formVisible" :title="formId ? '编辑日程' : '新建日程'" width="520px">
      <div class="ev-form">
        <label>标题</label>
        <el-input v-model="form.title" placeholder="如 李明轩❤陈思雨 约见" />
        <div class="form-row">
          <div>
            <label>日期</label>
            <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          </div>
          <div>
            <label>类型</label>
            <el-select v-model="form.type" style="width:100%">
              <el-option v-for="t in TYPE_OPTIONS" :key="t" :label="t" :value="t" />
            </el-select>
          </div>
        </div>
        <div class="form-row">
          <div>
            <label>开始</label>
            <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm" style="width:100%" />
          </div>
          <div>
            <label>结束</label>
            <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm" style="width:100%" />
          </div>
        </div>
        <label>地点</label>
        <el-input v-model="form.place" placeholder="如 柏悦下午茶" />
        <div class="form-row">
          <div>
            <label>负责红娘</label>
            <el-input v-model="form.agent" placeholder="红娘姓名" />
          </div>
          <div>
            <label>状态</label>
            <el-select v-model="form.status" style="width:100%">
              <el-option label="待确认" value="待确认" />
              <el-option label="已确认" value="已确认" />
              <el-option label="已完成" value="已完成" />
              <el-option label="已取消" value="已取消" />
            </el-select>
          </div>
        </div>
      </div>
      <template #footer>
        <div style="flex:1; display:flex; gap:8px">
          <button v-if="formId" class="btn btn-ghost" @click="onDelete">删除</button>
          <button v-if="formId" class="btn btn-outline" @click="onChangeStatus('已完成')">标记完成</button>
        </div>
        <button class="btn btn-ghost" @click="formVisible = false">取消</button>
        <button class="btn btn-gold" @click="saveEvent">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cal-page { display: flex; flex-direction: column; gap: 20px; }
.cal-top { display: flex; align-items: center; gap: 10px; }
.icon-btn { width: 32px; height: 32px; border-radius: 8px; border: 1px solid var(--dt-border); background: var(--dt-glass-light); color: var(--dt-ink-2); cursor: pointer; display: flex; align-items: center; justify-content: center; }
.icon-btn:hover { color: var(--dt-primary); border-color: var(--dt-primary-light); }
.view-toggle { display: flex; gap: 4px; background: var(--dt-glass-light); border: 1px solid var(--dt-border); border-radius: 999px; padding: 3px; }
.view-toggle span { padding: 5px 18px; border-radius: 999px; font-size: 0.8125rem; color: var(--dt-ink-3); cursor: pointer; }
.view-toggle span.on { background: var(--dt-gold-gradient); color: #fff; font-weight: 600; }

.cal-body { display: flex; gap: 20px; align-items: flex-start; }
.kpi-row { display: flex; gap: 16px; }
.kpi-card { flex: 1; display: flex; align-items: center; justify-content: space-between; background: var(--dt-glass-light); border: 1px solid var(--dt-border); border-radius: 14px; padding: 16px 18px; }
.kpi-label { font-size: 0.75rem; color: var(--dt-ink-3); }
.kpi-value { font-size: 1.5rem; font-weight: 800; color: var(--dt-ink-1); margin-top: 4px; }
.kpi-value .unit { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-3); margin-left: 2px; }
.kpi-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; color: #fff; }
.kpi-icon.gold { background: var(--dt-gold-gradient); }
.kpi-icon.burgundy { background: var(--dt-primary); }
.kpi-icon.warning { background: linear-gradient(135deg,#e0a44a,#c8842a); }
.kpi-icon.success { background: linear-gradient(135deg,#6fae7a,#4e8a5a); }

.list-pane { flex: 1; min-width: 0; padding: 18px; }
.list-title { font-size: 0.9375rem; font-weight: 800; color: var(--dt-ink-1); margin-bottom: 12px; }
.list-item { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 10px; margin-bottom: 8px; cursor: pointer; border: 1px solid var(--dt-border); }
.list-item:hover { border-color: var(--dt-primary-light); }
.li-time { font-size: 0.75rem; font-weight: 700; color: var(--dt-primary); width: 120px; flex-shrink: 0; }
.li-main { flex: 1; min-width: 0; }
.li-title { font-size: 0.875rem; font-weight: 600; color: var(--dt-ink-1); }
.li-sub { font-size: 0.75rem; color: var(--dt-ink-4); }
.li-status { font-size: 0.75rem; color: var(--dt-warning); }
.li-status.done { color: var(--dt-success); }
.side-empty { font-size: 0.8125rem; color: var(--dt-ink-4); padding: 8px 0; }

.ev-form label { display: block; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-2); margin: 12px 0 6px; }
.ev-form label:first-child { margin-top: 0; }
.form-row { display: flex; gap: 12px; }
.form-row > div { flex: 1; min-width: 0; }
</style>
