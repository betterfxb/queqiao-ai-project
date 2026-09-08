<script setup lang="ts">
/**
 * 约见日程 — 数据：GET /api/calendar/events
 * 周历动态生成本周（周一~周日）并标记今日；筛选 pills 真正过滤时间线。
 */
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { api, type CalendarEvent } from '@/api'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

const pad = (n: number) => (n < 10 ? '0' + n : '' + n)
const dateStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const isSameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日']

/* ---------- 周历（动态：本周一 ~ 周日，标记今日） ---------- */
const weekDays = ref<{ label: string; date: number; dateStr: string; weekend: boolean; today: boolean; dot: boolean }[]>([])
const buildWeek = () => {
  const now = new Date()
  const dow = now.getDay() // 0=周日
  const diffToMon = dow === 0 ? -6 : 1 - dow
  const mon = new Date(now)
  mon.setDate(now.getDate() + diffToMon)
  const arr: { label: string; date: number; dateStr: string; weekend: boolean; today: boolean; dot: boolean }[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(mon)
    d.setDate(mon.getDate() + i)
    const wd = d.getDay()
    arr.push({
      label: WEEK_LABELS[wd === 0 ? 6 : wd - 1],
      date: d.getDate(),
      dateStr: dateStr(d),
      weekend: wd === 0 || wd === 6,
      today: isSameDay(d, now),
      dot: false,
    })
  }
  weekDays.value = arr
}

/* ---------- 筛选 ---------- */
const filters = ['全部', '待确认', '已完成']
const activeFilter = ref('全部')

/* ---------- 数据 ---------- */
interface MeetingView {
  id: string
  date: string
  time: string
  place: string
  status: string
  title: string
  type: string
  note: string
  highlight: boolean
  memberIds?: string[]
  [k: string]: any
}
const meetings = ref<MeetingView[]>([])
const loading = ref(false)

const fetchList = async () => {
  loading.value = true
  try {
    const events: CalendarEvent[] = await api.calendarEvents()
    meetings.value = (events || []).map((e) => ({
      id: String(e.id),
      date: e.date || '',
      time: e.startTime || (e as any).time || '',
      place: e.place || '',
      status: e.status || '待确认',
      title: e.title || '约见',
      type: e.type || '约见',
      memberIds: (e as any).memberIds || [],
      note: (e as any).note || e.remark || e.title || '',
      highlight: !!(e as any).highlight,
    }))
  } catch {
    uni.showToast({ title: '后端未连接', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh?.()
  }
}

/* 周历圆点：按日期是否有约见 */
const refreshDots = () => {
  const counts: Record<string, number> = {}
  for (const m of meetings.value) counts[m.date] = (counts[m.date] || 0) + 1
  weekDays.value = weekDays.value.map((d) => ({ ...d, dot: (counts[d.dateStr] || 0) > 0 }))
}

onShow(async () => {
  buildWeek()
  await fetchList()
  refreshDots()
})
onPullDownRefresh(fetchList)

/* ---------- 筛选与统计 ---------- */
const isDone = (s: string) => s.includes('完成') || s.includes('已确认')
// activeFilter 真正过滤时间线（不再仅切样式）
const displayList = computed(() =>
  meetings.value
    .filter((m) =>
      activeFilter.value === '全部'
        ? true
        : activeFilter.value === '已完成'
          ? isDone(m.status)
          : !isDone(m.status)
    )
    .slice()
    .sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`))
)
// 按日期分组，渲染日期分隔
const grouped = computed(() => {
  const map = new Map<string, MeetingView[]>()
  for (const m of displayList.value) {
    const key = m.date || '未排期'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(m)
  }
  return [...map.entries()].map(([date, list]) => ({ date, list }))
})

const todayCount = computed(() => meetings.value.filter((m) => m.date === dateStr(new Date())).length)
const confirmedCount = computed(() => meetings.value.filter((m) => isDone(m.status)).length)
const pendingCount = computed(() => meetings.value.filter((m) => !isDone(m.status)).length)
const nextMeeting = computed(() => displayList.value[0])

const dotClass = (s: string) => (isDone(s) ? 'dot-success' : 'dot-warning')
const pillClass = (s: string) => (isDone(s) ? 'pill-success' : 'pill-warning')

const goBack = () => uni.navigateBack()

/* ---------- 新建约见（真实写入 POST /api/calendar/events） ---------- */
const EVENT_TYPES = ['约见', '相亲', '回访', '活动', '其他']
const createOpen = ref(false)
const creating = ref(false)
const createForm = ref({
  title: '',
  type: '约见',
  date: dateStr(new Date()),
  startTime: '10:00',
  place: '',
  remark: '',
})

const onNew = () => {
  // 每次打开重置为默认值，避免残留上次输入
  createForm.value = {
    title: '',
    type: '约见',
    date: dateStr(new Date()),
    startTime: '10:00',
    place: '',
    remark: '',
  }
  createOpen.value = true
}
const closeCreate = () => {
  createOpen.value = false
}

/** 时间合法性校验：HH:mm 且小时 0-23、分钟 0-59，防脏数据入库 */
const isValidTime = (t: string) => {
  const m = /^(\d{1,2}):(\d{2})$/.exec(t.trim())
  if (!m) return false
  const h = Number(m[1])
  const mi = Number(m[2])
  return h >= 0 && h <= 23 && mi >= 0 && mi <= 59
}

const submitCreate = async () => {
  if (creating.value) return
  const f = createForm.value
  if (!f.title.trim()) {
    uni.showToast({ title: '请填写约见标题', icon: 'none' })
    return
  }
  if (!f.date) {
    uni.showToast({ title: '请选择日期', icon: 'none' })
    return
  }
  if (!isValidTime(f.startTime)) {
    uni.showToast({ title: '时间格式应为 HH:mm', icon: 'none' })
    return
  }
  creating.value = true
  try {
    await api.createCalendarEvent({
      title: f.title.trim(),
      type: f.type,
      date: f.date,
      startTime: f.startTime.trim(),
      place: f.place.trim(),
      remark: f.remark.trim(),
      status: '待确认',
    })
    uni.showToast({ title: '约见已创建', icon: 'success' })
    createOpen.value = false
    buildWeek()
    await fetchList()
    refreshDots()
  } catch (e: any) {
    uni.showToast({ title: (e && e.message) || '创建失败', icon: 'none' })
  } finally {
    creating.value = false
  }
}
const onPickDate = (e: any) => {
  createForm.value.date = e?.detail?.value || createForm.value.date
}
const onPickTime = (e: any) => {
  createForm.value.startTime = e?.detail?.value || createForm.value.startTime
}

/* ---------- 约见详情（真实操作：完成 / 删除 / 联系会员） ---------- */
const detailOpen = ref(false)
const detailItem = ref<MeetingView | null>(null)
const acting = ref(false)

const onDetail = (m: MeetingView) => {
  detailItem.value = m
  detailOpen.value = true
}
const closeDetail = () => {
  detailOpen.value = false
}

/** 标记完成：后端无 PUT 单条接口，改用 POST 覆盖同 id 记录（status 置为已完成） */
const markDone = async () => {
  const m = detailItem.value
  if (!m || acting.value) return
  acting.value = true
  try {
    await api.createCalendarEvent({
      id: m.id,
      title: m.title,
      type: m.type,
      date: m.date,
      startTime: m.time,
      place: m.place,
      remark: m.note,
      status: '已完成',
    })
    uni.showToast({ title: '已标记为完成', icon: 'success' })
    detailOpen.value = false
    await fetchList()
    refreshDots()
  } catch (e: any) {
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  } finally {
    acting.value = false
  }
}

const removeEvent = async () => {
  const m = detailItem.value
  if (!m || acting.value) return
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '删除约见',
      content: `确认删除「${m.title}」？`,
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false),
    })
  })
  if (!ok) return
  acting.value = true
  try {
    await api.deleteCalendarEvent(m.id)
    uni.showToast({ title: '已删除', icon: 'success' })
    detailOpen.value = false
    await fetchList()
    refreshDots()
  } catch (e: any) {
    uni.showToast({ title: (e && e.message) || '删除失败', icon: 'none' })
  } finally {
    acting.value = false
  }
}

/** 联系会员：该约见有关联会员时直接进会话，否则引导去消息页 */
const onContact = async (m: MeetingView) => {
  const memberId = (m as any).memberIds?.[0] || (m as any).memberId
  if (!memberId) {
    uni.showToast({ title: '该约见未关联会员', icon: 'none' })
    return
  }
  try {
    const c = await api.createConversation(memberId)
    uni.navigateTo({ url: `/pages/message/chat?id=${c.id}&name=${encodeURIComponent(m.title)}` })
  } catch (e: any) {
    uni.showToast({ title: (e && e.message) || '无法发起会话', icon: 'none' })
  }
}
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="loading" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 顶栏 -->
    <view class="glass-bar topbar">
      <view class="circle-btn flex-center" hover-class="hover-opacity" @tap="goBack">
        <app-icon name="back-ink" size="40rpx" />
      </view>
      <text class="topbar-title">约见日程</text>
      <view class="new-btn flex-center" hover-class="hover-opacity" @tap="onNew">
        <app-icon name="calendar-plus-primary" size="40rpx" />
      </view>
    </view>

    <!-- 今日概览卡 -->
    <view class="overview-card flex-between">
      <view class="ov-left">
        <text class="ov-label">今日约见</text>
        <view class="flex items-baseline" style="gap: 8rpx; margin: 8rpx 0">
          <text class="ov-num">{{ todayCount }}</text>
          <text class="ov-unit">场</text>
        </view>
        <view v-if="nextMeeting" class="flex items-center" style="gap: 8rpx">
          <app-icon name="clock-ink" size="24rpx" />
          <text class="ov-next">下一场: {{ nextMeeting.time }} {{ nextMeeting.place }}</text>
        </view>
      </view>
      <view class="ov-pills">
        <view class="ov-pill flex items-center op-success">
          <app-icon name="check-circle-success" size="22rpx" />
          <text>{{ confirmedCount }} 已确认</text>
        </view>
        <view class="ov-pill flex items-center op-warning">
          <app-icon name="clock-warning" size="22rpx" />
          <text>{{ pendingCount }} 待确认</text>
        </view>
      </view>
    </view>

    <!-- 周历条 -->
    <glass-card padding="28rpx 24rpx" radius="40rpx" class="week-card">
      <view class="flex justify-around">
        <view
          v-for="d in weekDays"
          :key="d.date"
          class="day-col flex-center"
          :class="{ 'day-today': d.today }"
        >
          <text class="day-label" :class="{ 'day-weekend': d.weekend }">{{ d.label }}</text>
          <view class="day-date flex-center" :class="{ 'date-today': d.today }">{{ d.date }}</view>
          <view class="day-dot" :class="{ 'dot-on': d.dot, 'dot-today': d.today }" />
        </view>
      </view>
    </glass-card>

    <!-- 筛选 pills -->
    <view class="flex" style="gap: 16rpx; margin: 32rpx 40rpx 0">
      <view
        v-for="f in filters"
        :key="f"
        class="filter-pill flex-center"
        :class="activeFilter === f ? (f === '全部' ? 'fp-active' : f === '待确认' ? 'fp-warning' : 'fp-success') : ''"
        @tap="activeFilter = f"
      >{{ f }}</view>
    </view>

    <!-- 时间线列表（按 activeFilter 过滤，按日期分组） -->
    <view class="timeline">
      <template v-for="g in grouped" :key="g.date">
        <view class="day-divider flex items-center">
          <text class="dd-text">{{ g.date }}</text>
          <view class="dd-line" />
        </view>
        <view v-for="(m, i) in g.list" :key="m.id" class="meeting-item flex">
          <view class="t-rail">
            <text class="t-time">{{ m.time }}</text>
            <view class="t-dot" :class="dotClass(m.status)" />
            <view v-if="i < g.list.length - 1" class="t-line" />
          </view>
          <glass-card strong padding="28rpx 32rpx" radius="36rpx" class="m-card">
            <view class="flex-between items-center">
              <text class="m-title">{{ m.title }}</text>
              <text class="m-pill" :class="pillClass(m.status)">{{ m.status }}</text>
            </view>
            <view class="flex items-center m-row">
              <app-icon name="map-pin-ink" size="26rpx" />
              <text class="m-place">{{ m.place }}</text>
            </view>
            <view class="flex items-center m-row">
              <app-icon :name="m.highlight ? 'sparkles-gold' : 'file-ink'" size="24rpx" />
              <text class="m-note">{{ m.note }}</text>
            </view>
            <view class="flex" style="gap: 28rpx; margin-top: 20rpx">
              <view class="m-link flex items-center" hover-class="hover-opacity" @tap="onDetail(m)">
                <text class="m-link-primary">查看详情</text>
                <app-icon name="chevron-right" size="24rpx" />
              </view>
              <view class="m-link flex items-center" hover-class="hover-opacity" @tap="onContact(m)">
                <app-icon name="phone-gold" size="24rpx" />
                <text class="m-link-gold">联系会员</text>
              </view>
            </view>
          </glass-card>
        </view>
      </template>

      <view v-if="grouped.length === 0" class="empty flex-center">
        <text class="empty-text">当前筛选下暂无约见安排</text>
      </view>
    </view>

    <!-- FAB -->
    <view class="fab flex-center" hover-class="hover-scale" @tap="onNew">
      <app-icon name="plus-white" size="48rpx" />
    </view>

    <!-- 空态引导：无约见时给出新建入口（非空态时不显示） -->
    <view v-if="grouped.length === 0 && !loading" class="empty-guide">
      <text class="eg-text">还没有约见安排</text>
      <view class="eg-btn flex-center" hover-class="hover-opacity" @tap="onNew">
        <app-icon name="calendar-plus-primary" size="30rpx" />
        <text class="eg-btn-text">新建第一场约见</text>
      </view>
    </view>

    <view style="height: 160rpx" />

    <!-- ===== 新建约见弹层 ===== -->
    <view v-if="createOpen" class="sheet-mask" @tap="closeCreate" />
    <view class="sheet" :class="{ 'sheet-open': createOpen }">
      <view class="sheet-head flex-between items-center">
        <text class="sheet-title">新建约见</text>
        <view class="sheet-close flex-center" hover-class="hover-opacity" @tap="closeCreate">
          <app-icon name="close-ink" size="32rpx" />
        </view>
      </view>

      <view class="sheet-body">
        <view class="f-row">
          <text class="f-label">标题</text>
          <input v-model="createForm.title" class="f-input" placeholder="如：张先生 & 李女士 咖啡厅见面" placeholder-class="f-ph" />
        </view>

        <view class="f-row">
          <text class="f-label">类型</text>
          <view class="flex flex-wrap" style="gap: 12rpx">
            <view
              v-for="t in EVENT_TYPES"
              :key="t"
              class="f-pill flex-center"
              :class="{ 'f-pill-active': createForm.type === t }"
              @tap="createForm.type = t"
            >{{ t }}</view>
          </view>
        </view>

        <view class="f-row">
          <text class="f-label">日期</text>
          <picker mode="date" :value="createForm.date" @change="onPickDate">
            <view class="f-picker flex-between items-center">
              <text class="f-picker-text">{{ createForm.date }}</text>
              <app-icon name="chevron-right" size="24rpx" />
            </view>
          </picker>
        </view>

        <view class="f-row">
          <text class="f-label">时间</text>
          <picker mode="time" :value="createForm.startTime" @change="onPickTime">
            <view class="f-picker flex-between items-center">
              <text class="f-picker-text">{{ createForm.startTime }}</text>
              <app-icon name="chevron-right" size="24rpx" />
            </view>
          </picker>
        </view>

        <view class="f-row">
          <text class="f-label">地点</text>
          <input v-model="createForm.place" class="f-input" placeholder="如：星巴克（国贸店）" placeholder-class="f-ph" />
        </view>

        <view class="f-row" style="border-bottom: none">
          <text class="f-label">备注</text>
          <textarea
            v-model="createForm.remark"
            class="f-textarea"
            placeholder="如：双方均确认到场，提前 10 分钟到"
            placeholder-class="f-ph"
            :maxlength="200"
          />
        </view>
      </view>

      <view class="sheet-submit flex-center" :class="{ 'is-busy': creating }" hover-class="hover-scale" @tap="submitCreate">
        {{ creating ? '创建中…' : '确认创建' }}
      </view>
    </view>

    <!-- ===== 约见详情弹层 ===== -->
    <view v-if="detailOpen" class="sheet-mask" @tap="closeDetail" />
    <view class="sheet" :class="{ 'sheet-open': detailOpen }">
      <view class="sheet-head flex-between items-center">
        <text class="sheet-title">约见详情</text>
        <view class="sheet-close flex-center" hover-class="hover-opacity" @tap="closeDetail">
          <app-icon name="close-ink" size="32rpx" />
        </view>
      </view>

      <view v-if="detailItem" class="detail-body">
        <view class="d-title-row">
          <text class="d-title">{{ detailItem.title }}</text>
          <text class="m-pill" :class="pillClass(detailItem.status)">{{ detailItem.status }}</text>
        </view>
        <view class="d-line flex items-center">
          <app-icon name="calendar-plus-primary" size="26rpx" />
          <text class="d-text">{{ detailItem.date }} {{ detailItem.time }}</text>
        </view>
        <view class="d-line flex items-center">
          <app-icon name="map-pin-ink" size="26rpx" />
          <text class="d-text">{{ detailItem.place || '地点未填写' }}</text>
        </view>
        <view class="d-line flex items-center">
          <app-icon name="file-ink" size="26rpx" />
          <text class="d-text">类型：{{ detailItem.type }}</text>
        </view>
        <view class="d-line flex items-start">
          <app-icon name="file-ink" size="26rpx" />
          <text class="d-text" style="flex: 1">{{ detailItem.note || '暂无备注' }}</text>
        </view>

        <view class="d-actions">
          <view class="d-btn d-primary flex-center" hover-class="hover-opacity" @tap="onContact(detailItem)">
            <app-icon name="phone-gold" size="30rpx" />
            <text class="d-btn-text">联系会员</text>
          </view>
          <view class="d-btn d-success flex-center" :class="{ 'is-busy': acting }" hover-class="hover-opacity" @tap="markDone">
            <app-icon name="check-circle-success" size="30rpx" />
            <text class="d-btn-text">标记完成</text>
          </view>
          <view class="d-btn d-danger flex-center" :class="{ 'is-busy': acting }" hover-class="hover-opacity" @tap="removeEvent">
            <app-icon name="file-ink" size="30rpx" />
            <text class="d-btn-text">删除约见</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: transparent;
}
.topbar {
  position: relative;
  z-index: 2;
  width: 100%;
  padding: 20rpx 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-sizing: border-box;
}
.topbar-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.circle-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow-soft);
}
.new-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: var(--q-primary-soft);
  border: 1px solid var(--q-primary-soft-2);
}

/* ---------- 今日概览卡 ---------- */
.overview-card {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
  border-radius: 40rpx;
  padding: 32rpx 36rpx;
  background: linear-gradient(135deg, rgba(139, 37, 74, 0.08), rgba(212, 175, 55, 0.1));
  border: 1px solid rgba(139, 37, 74, 0.12);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  align-items: flex-start;
}
.ov-left {
  flex: 1;
  min-width: 0;
}
.ov-label {
  font-size: 26rpx;
  color: var(--q-ink-2);
}
.ov-num {
  font-size: 64rpx;
  font-weight: 700;
  color: var(--q-primary);
  line-height: 1;
}
.ov-unit {
  font-size: 28rpx;
  color: var(--q-primary);
  font-weight: 500;
}
.ov-next {
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.ov-pills {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  align-items: flex-end;
  flex-shrink: 0;
}
.ov-pill {
  gap: 8rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 500;
  white-space: nowrap;
}
.op-success {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.op-warning {
  background: var(--q-warning-soft);
  color: var(--q-warning);
}

/* ---------- 周历 ---------- */
.week-card {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
}
.day-col {
  flex-direction: column;
  gap: 10rpx;
  padding: 16rpx 8rpx;
  border-radius: 28rpx;
  min-width: 80rpx;
}
.day-today {
  background: var(--q-primary-soft);
}
.day-label {
  font-size: 22rpx;
  color: var(--q-ink-3);
}
.day-weekend {
  color: var(--q-primary-lighter);
}
.day-date {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.date-today {
  background: var(--q-primary);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 8rpx 20rpx rgba(139, 37, 74, 0.3);
}
.day-dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: transparent;
}
.dot-on {
  background: var(--q-primary);
}
.dot-today {
  background: var(--q-gold);
  width: 10rpx;
  height: 10rpx;
}

/* ---------- 筛选 pills ---------- */
.filter-pill {
  position: relative;
  z-index: 2;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 500;
  color: var(--q-ink-3);
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
}
.fp-active {
  background: var(--q-primary);
  color: #fff;
  border-color: var(--q-primary);
}
.fp-warning {
  background: var(--q-warning-soft);
  color: var(--q-warning);
  border-color: transparent;
}
.fp-success {
  background: var(--q-success-soft);
  color: var(--q-success);
  border-color: transparent;
}

/* ---------- 时间线 ---------- */
.timeline {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
}
.meeting-item {
  gap: 24rpx;
  padding-bottom: 28rpx;
}
.t-rail {
  width: 88rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.t-time {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--q-primary);
  margin-bottom: 12rpx;
}
.t-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  border: 4rpx solid var(--q-bg-base);
  background: var(--q-primary);
  flex-shrink: 0;
}
.dot-success {
  background: var(--q-success);
  box-shadow: 0 0 0 6rpx var(--q-success-soft);
}
.dot-warning {
  background: var(--q-warning);
  box-shadow: 0 0 0 6rpx var(--q-warning-soft);
}
.t-line {
  flex: 1;
  width: 2rpx;
  background: rgba(139, 37, 74, 0.1);
  margin-top: 12rpx;
}
.m-card {
  flex: 1;
  min-width: 0;
}
.m-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.m-pill {
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
  flex-shrink: 0;
}
.pill-success {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.pill-warning {
  background: var(--q-warning-soft);
  color: var(--q-warning);
}
.m-row {
  gap: 10rpx;
  margin-top: 12rpx;
}
.m-place {
  font-size: 24rpx;
  color: var(--q-ink-2);
}
.m-note {
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.m-link-primary {
  font-size: 24rpx;
  color: var(--q-primary);
  font-weight: 500;
}
.m-link-gold {
  font-size: 24rpx;
  color: var(--q-gold-dark);
  font-weight: 500;
}
.day-divider {
  gap: 20rpx;
  margin: 8rpx 0 28rpx;
}
.dd-text {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--q-ink-3);
  white-space: nowrap;
}
.dd-line {
  flex: 1;
  height: 1px;
  background: rgba(139, 37, 74, 0.08);
}

/* ---------- FAB ---------- */
.fab {
  position: fixed;
  right: 40rpx;
  bottom: 48rpx;
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: var(--q-primary);
  border: 6rpx solid #fff;
  box-shadow: 0 16rpx 48rpx rgba(139, 37, 74, 0.35);
  z-index: 40;
}

.hover-opacity {
  opacity: 0.6;
}
.hover-scale {
  transform: scale(1.08);
  transition: transform 0.15s ease;
}

/* ---------- 空态引导 ---------- */
.empty-guide {
  position: relative;
  z-index: 2;
  margin: 24rpx 40rpx 0;
  padding: 48rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
}
.eg-text {
  font-size: 28rpx;
  color: var(--q-ink-3);
}
.eg-btn {
  gap: 10rpx;
  height: 88rpx;
  padding: 0 44rpx;
  border-radius: 999rpx;
  background: var(--q-primary);
  box-shadow: 0 12rpx 28rpx rgba(139, 37, 74, 0.28);
}
.eg-btn-text {
  font-size: 28rpx;
  font-weight: 700;
  color: #fff;
}

/* ---------- 底部弹层 ---------- */
.sheet-mask {
  position: fixed;
  inset: 0;
  background: rgba(46, 34, 42, 0.35);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  z-index: 98;
}
.sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 86vh;
  border-radius: 48rpx 48rpx 0 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border-top: 1px solid var(--q-glass-border);
  box-shadow: 0 -20rpx 60rpx rgba(120, 70, 80, 0.18);
  transform: translateY(110%);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  z-index: 99;
  padding: 36rpx 40rpx calc(36rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  overflow-y: auto;
}
.sheet-open {
  transform: translateY(0);
}
.sheet-head {
  margin-bottom: 12rpx;
}
.sheet-title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.sheet-close {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(150, 128, 137, 0.12);
}
.sheet-body {
  padding-top: 8rpx;
}
.f-row {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1px solid rgba(139, 37, 74, 0.07);
}
.f-label {
  width: 130rpx;
  flex-shrink: 0;
  font-size: 28rpx;
  color: var(--q-ink-2);
  font-weight: 500;
}
.f-input {
  flex: 1;
  font-size: 30rpx;
  color: var(--q-ink-1);
  background: var(--q-glass-bg-light);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 20rpx;
  padding: 16rpx 22rpx;
}
.f-ph {
  color: var(--q-ink-4);
  font-size: 26rpx;
}
.f-textarea {
  flex: 1;
  height: 140rpx;
  font-size: 28rpx;
  color: var(--q-ink-1);
  background: var(--q-glass-bg-light);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 20rpx;
  padding: 16rpx 22rpx;
  line-height: 1.6;
}
.f-pill {
  padding: 10rpx 26rpx;
  border-radius: 999rpx;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  font-size: 24rpx;
  color: var(--q-ink-2);
}
.f-pill-active {
  background: var(--q-primary);
  border-color: var(--q-primary);
  color: #fff;
  font-weight: 600;
}
.f-picker {
  flex: 1;
  font-size: 30rpx;
  color: var(--q-ink-1);
  background: var(--q-glass-bg-light);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 20rpx;
  padding: 16rpx 22rpx;
}
.f-picker-text {
  font-size: 30rpx;
  color: var(--q-ink-1);
}
.sheet-submit {
  margin-top: 32rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--q-gold-gradient);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 28rpx rgba(168, 134, 30, 0.35);
}
.sheet-submit.is-busy {
  opacity: 0.7;
}

/* ---------- 详情弹层 ---------- */
.detail-body {
  padding-top: 8rpx;
}
.d-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 20rpx;
}
.d-title {
  flex: 1;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.d-line {
  gap: 12rpx;
  padding: 18rpx 0;
  border-bottom: 1px solid rgba(139, 37, 74, 0.06);
}
.d-text {
  font-size: 28rpx;
  color: var(--q-ink-2);
}
.d-actions {
  margin-top: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.d-btn {
  gap: 12rpx;
  height: 92rpx;
  border-radius: 24rpx;
}
.d-btn-text {
  font-size: 28rpx;
  font-weight: 700;
}
.d-primary {
  background: var(--q-gold-soft);
  border: 2rpx solid rgba(212, 175, 55, 0.35);
}
.d-primary .d-btn-text {
  color: var(--q-gold-dark);
}
.d-success {
  background: var(--q-success-soft);
  border: 2rpx solid rgba(45, 156, 108, 0.3);
}
.d-success .d-btn-text {
  color: var(--q-success);
}
.d-danger {
  background: rgba(196, 62, 62, 0.08);
  border: 2rpx solid rgba(196, 62, 62, 0.25);
}
.d-danger .d-btn-text {
  color: #c43e3e;
}
.d-btn.is-busy {
  opacity: 0.6;
}
</style>
