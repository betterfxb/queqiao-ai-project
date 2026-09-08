<script setup lang="ts">
/**
 * 牵线详情 — B 端独立牵线详情页
 * 数据：GET /api/pairs/:id → { pair: Pair; male?: Member; female?: Member; events: CalendarEvent[] }
 * 能力：状态流转(updatePairStatus) / 安排约见(pairMeeting) / 发消息(createConversation) / 在看板查看
 * 视觉严格对齐移动端 Design Tokens（与 member/detail、kanban 一致）
 */
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { api, type Member, type Pair, type CalendarEvent, errMsg } from '@/api'
import { toDisplay, canAction, nextAction, type PairAction } from '@/utils/pairStatus'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

const id = ref('')
const pair = ref<Pair | null>(null)
const male = ref<Member | null>(null)
const female = ref<Member | null>(null)
const events = ref<CalendarEvent[]>([])
const loading = ref(false)

/* ---------- 状态归一（与后端真实生命周期、看板一致） ---------- */
const STATUS_NORMALIZE: Record<string, string> = {
  待确认: '待确认',
  初步推荐: '牵线中', 牵线中: '牵线中', 已联系: '牵线中', 跟进中: '牵线中', 待反馈: '牵线中',
  已约见: '已约见', 已脱单: '已脱单', 已成功: '已脱单', 已终止: '已终止',
  matching: '牵线中', pending: '待确认', met: '已约见', success: '已脱单', failed: '已终止', done: '已脱单',
}
const normalizeStatus = (s?: string) => STATUS_NORMALIZE[s || ''] || '待确认'

// 牵线进度步进：发起牵线 → 双方沟通 → 约见确认 → 完成牵线
const STEP_ORDER = ['待确认', '牵线中', '已约见', '已脱单'] as const
const currentStep = computed(() => {
  const s = normalizeStatus(pair.value?.status)
  if (s === '已终止') return -1
  const idx = STEP_ORDER.indexOf(s as (typeof STEP_ORDER)[number])
  return idx
})

/* ---------- 头像底色（列表接口未必返回 avatarBg，本地确定性取色） ---------- */
const AVATAR_PALETTE = [
  'linear-gradient(135deg,#C8D8C0,#5B8C5A)',
  'linear-gradient(135deg,#F0C0C8,#C76B82)',
  'linear-gradient(135deg,#B8C6E0,#6A7FB5)',
  'linear-gradient(135deg,#E3C766,#D4AF37)',
  'linear-gradient(135deg,#E0B8A0,#B5784F)',
  'linear-gradient(135deg,#C0B8E0,#7A6AB5)',
]
const avatarColor = (name?: string) => {
  const s = name || ''
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  return AVATAR_PALETTE[h % AVATAR_PALETTE.length]
}

const maleBg = computed(() => male.value?.avatarBg || avatarColor(pair.value?.maleName))
const femaleBg = computed(() => female.value?.avatarBg || avatarColor(pair.value?.femaleName))
const statusLabel = computed(() => toDisplay(pair.value?.statusLabel || pair.value?.status || '待确认'))
const isTerminated = computed(() => normalizeStatus(pair.value?.status) === '已终止')

/* ---------- 双方资料网格 ---------- */
const profileGrid = (m?: Member) => {
  if (!m) return []
  return [
    { label: '年龄', value: m.age ? `${m.age}岁` : '—' },
    { label: '身高', value: m.height ? `${m.height}cm` : '—' },
    { label: '学历', value: m.education || '—' },
    { label: '职业', value: m.job || '—' },
    { label: '城市', value: m.city || '—' },
    { label: '籍贯', value: m.hometown || '—' },
    { label: '年收入', value: m.income || '—' },
    { label: '婚姻', value: m.marriage || '—' },
    { label: '房产', value: m.house || '—' },
    { label: '座驾', value: m.car || '—' },
  ]
}

const fetchDetail = async () => {
  if (!id.value) return
  loading.value = true
  try {
    const r = await api.pairDetail(id.value)
    pair.value = r.pair || null
    male.value = r.male || null
    female.value = r.female || null
    events.value = r.events || []
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    loading.value = false
  }
}
onLoad((q) => {
  id.value = String(q?.id || '')
  if (id.value) fetchDetail()
})

/* ---------- 状态流转（与看板 ACTION_STATUS 对齐；done 发后端枚举「已成功」以触发分佣/积分） ---------- */
const LABEL_MAP: Record<string, string> = { call: '跟进', schedule: '约见', done: '完成', fail: '失败' }
const ACTION_STATUS: Record<string, string> = { call: '牵线中', schedule: '已约见', done: '已成功', fail: '已终止' }
/** 当前状态是否允许该动作（对齐后端状态机，前端禁用非法按钮） */
const actionAllowed = (type: PairAction) => canAction(type, normalizeStatus(pair.value?.status))
/** 推荐的下一步动作（用于高亮引导） */
const recommendedAction = computed<PairAction | null>(() => nextAction(normalizeStatus(pair.value?.status)))
const acting = ref(false)
const onAction = async (type: PairAction) => {
  if (!actionAllowed(type)) {
    uni.showToast({ title: '当前状态不支持该操作', icon: 'none' })
    return
  }
  const next = ACTION_STATUS[type]
  const label = LABEL_MAP[type]
  acting.value = true
  try {
    await api.updatePairStatus(String(id.value), next)
    if (pair.value) pair.value.status = next
    uni.showToast({ title: `${label}成功`, icon: 'success' })
  } catch (e: any) {
    const msg = errMsg(e)
    uni.showToast({ title: msg.includes('状态') ? '状态流转不被允许' : `${label}失败`, icon: 'none' })
  } finally {
    acting.value = false
  }
}

/* ---------- 安排约见（内联表单） ---------- */
const meetMode = ref(false)
const meetDate = ref('')
const meetTime = ref('')
const meetPlace = ref('')
const meetSaving = ref(false)
const today = () => {
  const d = new Date()
  const p = (n: number) => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const onSaveMeeting = async () => {
  if (!meetDate.value) {
    uni.showToast({ title: '请选择约见日期', icon: 'none' })
    return
  }
  meetSaving.value = true
  try {
    const r = await api.pairMeeting(String(id.value), {
      date: meetDate.value,
      startTime: meetTime.value || undefined,
      place: meetPlace.value || undefined,
    })
    if (r.event) events.value = [r.event, ...events.value]
    meetMode.value = false
    meetDate.value = ''
    meetTime.value = ''
    meetPlace.value = ''
    uni.showToast({ title: '约见已安排', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    meetSaving.value = false
  }
}

/* ---------- 发消息给某一方 ---------- */
const onMessageSide = async (side: 'male' | 'female') => {
  const m = side === 'male' ? male.value : female.value
  if (!m?.id) {
    uni.showToast({ title: '无该会员信息', icon: 'none' })
    return
  }
  try {
    const c = await api.createConversation(m.id)
    uni.navigateTo({ url: `/pages/message/chat?id=${c.id}` })
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  }
}

/* ---------- 记录反馈（pairFeedback） ---------- */
const FEED_KINDS = ['同意牵线', '有意向', '考虑中', '已拒绝', '已联系']
const feedMode = ref(false)
const feedSide = ref<'male' | 'female'>('male')
const feedKind = ref('')
const feedText = ref('')
const feedSaving = ref(false)
const onSaveFeedback = async () => {
  if (!feedText.value.trim()) {
    uni.showToast({ title: '请填写反馈内容', icon: 'none' })
    return
  }
  feedSaving.value = true
  try {
    const r = await api.pairFeedback(String(id.value), {
      side: feedSide.value,
      text: feedText.value.trim(),
      kind: feedKind.value || undefined,
    })
    if (r && r.id) pair.value = r
    feedMode.value = false
    feedText.value = ''
    feedKind.value = ''
    uni.showToast({ title: '反馈已记录', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    feedSaving.value = false
  }
}

/* ---------- 导航 ---------- */
const goBack = () => uni.navigateBack()
const goKanban = () => uni.navigateTo({ url: `/pages/followup/kanban?id=${id.value}` })

const eventStatusCls = (s?: string) =>
  s === '已完成' || s === '已确认' ? 'ev-done' : s === '已取消' ? 'ev-cancel' : 'ev-wait'
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="loading" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 顶栏 -->
    <view class="glass-bar topbar">
      <view class="circle-btn flex-center" hover-class="hover-opacity" @tap="goBack">
        <app-icon name="back-ink" size="44rpx" />
      </view>
      <text class="topbar-title">牵线详情</text>
      <view class="circle-btn flex-center" hover-class="hover-opacity" @tap="goKanban">
        <app-icon name="history-ink" size="36rpx" />
      </view>
    </view>

    <template v-if="pair">
      <!-- 状态 + 匹配度横幅 -->
      <view class="banner glass-strong">
        <view class="banner-top">
          <view class="status-pill" :class="isTerminated ? 'ps-term' : ''">
            <text class="pulse" v-if="!isTerminated" />
            <text>{{ statusLabel }}</text>
          </view>
          <view class="banner-meta">
            <text class="match-id">编号 {{ pair.id }}</text>
            <text class="match-date" v-if="pair.createdAt">创建于 {{ pair.createdAt.slice(0, 10) }}</text>
          </view>
        </view>

        <!-- 双方 + 匹配度 -->
        <view class="duo-hero">
          <view class="duo-side">
            <view class="duo-avatar flex-center" :style="{ background: maleBg }">{{ (pair.maleName || '男')[0] }}</view>
            <text class="duo-name">{{ pair.maleName || '男嘉宾' }}</text>
            <text class="duo-sub" v-if="male?.age">{{ male.age }}岁 · {{ male.gender }}</text>
          </view>

          <view class="ring-zone">
            <progress-ring :percent="typeof pair.score === 'number' ? pair.score : 0" size="140rpx" stroke="12rpx" />
            <text class="ring-pct">{{ pair.score ?? '—' }}</text>
            <text class="ring-cap">匹配度</text>
          </view>

          <view class="duo-side">
            <view class="duo-avatar flex-center" :style="{ background: femaleBg }">{{ (pair.femaleName || '女')[0] }}</view>
            <text class="duo-name">{{ pair.femaleName || '女嘉宾' }}</text>
            <text class="duo-sub" v-if="female?.age">{{ female.age }}岁 · {{ female.gender }}</text>
          </view>
        </view>
      </view>

      <!-- 牵线进度步进 -->
      <view class="steps glass-strong">
        <view v-if="isTerminated" class="step-term-tip">该牵线已终止</view>
        <view v-else class="steps-row">
          <view
            v-for="(s, i) in STEP_ORDER"
            :key="s"
            class="step"
            :class="i < currentStep ? 'done' : i === currentStep ? 'active' : 'pending'"
          >
            <view class="step-dot">
              <text v-if="i < currentStep" class="step-check">✓</text>
            </view>
            <text class="step-label">{{ ['发起牵线', '双方沟通', '约见确认', '完成牵线'][i] }}</text>
            <view v-if="i < STEP_ORDER.length - 1" class="step-line" :class="{ filled: i < currentStep }" />
          </view>
        </view>
      </view>

      <!-- 双方资料 -->
      <view class="section-title card-title-text">双方资料</view>
      <view class="profiles">
        <glass-card strong padding="32rpx" radius="36rpx" v-if="male">
          <view class="pf-head">
            <view class="pf-avatar flex-center" :style="{ background: maleBg }">{{ male.name[0] }}</view>
            <view>
              <text class="pf-name">{{ male.name }}<text class="pf-gender"> · {{ male.gender }}</text></text>
              <text class="pf-sub" v-if="male.city || male.job">{{ male.city }} · {{ male.job }}</text>
            </view>
          </view>
          <view class="pf-grid">
            <view v-for="g in profileGrid(male)" :key="g.label">
              <text class="pf-label">{{ g.label }}</text>
              <text class="pf-value">{{ g.value }}</text>
            </view>
          </view>
        </glass-card>
        <glass-card strong padding="32rpx" radius="36rpx" v-if="female">
          <view class="pf-head">
            <view class="pf-avatar flex-center" :style="{ background: femaleBg }">{{ female.name[0] }}</view>
            <view>
              <text class="pf-name">{{ female.name }}<text class="pf-gender"> · {{ female.gender }}</text></text>
              <text class="pf-sub" v-if="female.city || female.job">{{ female.city }} · {{ female.job }}</text>
            </view>
          </view>
          <view class="pf-grid">
            <view v-for="g in profileGrid(female)" :key="g.label">
              <text class="pf-label">{{ g.label }}</text>
              <text class="pf-value">{{ g.value }}</text>
            </view>
          </view>
        </glass-card>
        <view v-if="!male && !female" class="pf-empty">双方资料暂未同步</view>
      </view>

      <!-- 反馈 / 双方意向 -->
      <view class="flex-between" style="margin: 8rpx 40rpx 0">
        <view class="section-title card-title-text">反馈意向</view>
        <view class="add-btn flex-center" hover-class="hover-opacity" @tap="feedMode = !feedMode">
          <app-icon name="plus-primary" size="26rpx" />
          <text>{{ feedMode ? '收起' : '记录反馈' }}</text>
        </view>
      </view>
      <view class="meet-form glass-soft" v-if="feedMode">
        <view class="mf-row">
          <text class="mf-label">对象</text>
          <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
            <view class="sel-pill flex-center" :class="{ 'sel-active': feedSide === 'male' }" @tap="feedSide = 'male'">男方</view>
            <view class="sel-pill flex-center" :class="{ 'sel-active': feedSide === 'female' }" @tap="feedSide = 'female'">女方</view>
          </view>
        </view>
        <view class="mf-row">
          <text class="mf-label">意向</text>
          <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
            <view
              v-for="k in FEED_KINDS"
              :key="k"
              class="sel-pill flex-center"
              :class="{ 'sel-active': feedKind === k }"
              @tap="feedKind = k"
            >{{ k }}</view>
          </view>
        </view>
        <view class="mf-row" style="flex-direction: column; align-items: stretch; gap: 12rpx">
          <text class="mf-label">内容</text>
          <textarea v-model="feedText" class="fu-textarea" placeholder="记录该方对牵线的反馈…" :maxlength="500" />
        </view>
        <view class="mf-submit flex-center" :class="{ disabled: feedSaving }" @tap="onSaveFeedback">
          {{ feedSaving ? '保存中…' : '保存反馈' }}
        </view>
      </view>
      <glass-card strong padding="32rpx" radius="36rpx">
        <view v-if="pair.reason" class="reason-box">
          <text class="reason-text">{{ pair.reason }}</text>
        </view>
        <view class="fb-row" v-if="pair.maleResponse || pair.maleResponseKind">
          <text class="fb-side fb-male">男方</text>
          <view class="fb-body">
            <text class="fb-kind" v-if="pair.maleResponseKind">{{ pair.maleResponseKind }}</text>
            <text class="fb-text">{{ pair.maleResponse }}</text>
          </view>
        </view>
        <view class="fb-row" v-if="pair.femaleResponse || pair.femaleResponseKind">
          <text class="fb-side fb-female">女方</text>
          <view class="fb-body">
            <text class="fb-kind" v-if="pair.femaleResponseKind">{{ pair.femaleResponseKind }}</text>
            <text class="fb-text">{{ pair.femaleResponse }}</text>
          </view>
        </view>
        <view v-if="!pair.reason && !pair.maleResponse && !pair.femaleResponse" class="fb-empty">
          暂无双方反馈，红娘可在沟通中补充
        </view>
      </glass-card>

      <!-- 约见记录 -->
      <view class="flex-between" style="margin: 8rpx 40rpx 0">
        <view class="section-title card-title-text">约见记录</view>
        <view class="add-btn flex-center" hover-class="hover-opacity" @tap="meetMode = !meetMode">
          <app-icon name="plus-primary" size="26rpx" />
          <text>{{ meetMode ? '收起' : '安排约见' }}</text>
        </view>
      </view>

      <view class="meet-form glass-soft" v-if="meetMode">
        <view class="mf-row">
          <text class="mf-label">日期</text>
          <picker mode="date" :value="meetDate" :start="today()" @change="(e: any) => (meetDate = e.detail.value)">
            <view class="mf-picker">{{ meetDate || '选择日期' }}</view>
          </picker>
        </view>
        <view class="mf-row">
          <text class="mf-label">时间</text>
          <picker mode="time" :value="meetTime" @change="(e: any) => (meetTime = e.detail.value)">
            <view class="mf-picker">{{ meetTime || '选填' }}</view>
          </picker>
        </view>
        <view class="mf-row">
          <text class="mf-label">地点</text>
          <input v-model="meetPlace" class="mf-input" placeholder="选填，如：星巴克(国贸店)" />
        </view>
        <view class="mf-submit flex-center" :class="{ disabled: meetSaving }" @tap="onSaveMeeting">
          {{ meetSaving ? '保存中…' : '确认安排' }}
        </view>
      </view>

      <view class="events">
        <view v-for="ev in events" :key="ev.id" class="ev-row glass-soft">
          <view class="ev-dot" :class="eventStatusCls(ev.status)" />
          <view class="ev-body">
            <view class="flex-between">
              <text class="ev-title">{{ ev.title || '约见' }}</text>
              <text class="ev-status" :class="eventStatusCls(ev.status)">{{ ev.status || '待确认' }}</text>
            </view>
            <text class="ev-meta" v-if="ev.date || ev.startTime">📅 {{ ev.date }}<text v-if="ev.startTime"> {{ ev.startTime }}</text></text>
            <text class="ev-meta" v-if="ev.place">📍 {{ ev.place }}</text>
            <text class="ev-meta" v-if="ev.remark">📝 {{ ev.remark }}</text>
          </view>
        </view>
        <view v-if="!events.length" class="ev-empty">暂无约见安排</view>
      </view>

      <!-- 操作行 -->
      <view class="op-bar">
        <view
          class="op-btn op-call flex-center"
          :class="{ 'op-disabled': !actionAllowed('call'), 'op-reco': recommendedAction === 'call' }"
          hover-class="hover-scale"
          @tap="onAction('call')"
        >
          <text>📞 跟进</text>
        </view>
        <view
          class="op-btn op-schedule flex-center"
          :class="{ 'op-disabled': !actionAllowed('schedule'), 'op-reco': recommendedAction === 'schedule' }"
          hover-class="hover-scale"
          @tap="onAction('schedule')"
        >
          <text>📅 约见</text>
        </view>
        <view
          class="op-btn op-done flex-center"
          :class="{ 'op-disabled': !actionAllowed('done'), 'op-reco': recommendedAction === 'done' }"
          hover-class="hover-scale"
          @tap="onAction('done')"
        >
          <text>✅ 完成</text>
        </view>
        <view
          class="op-btn op-fail flex-center"
          :class="{ 'op-disabled': !actionAllowed('fail'), 'op-reco': recommendedAction === 'fail' }"
          hover-class="hover-scale"
          @tap="onAction('fail')"
        >
          <text>❌ 失败</text>
        </view>
      </view>

      <!-- 发消息 -->
      <view class="msg-row">
        <view class="msg-btn flex-center" hover-class="hover-opacity" @tap="onMessageSide('male')">
          <app-icon name="message-primary" size="30rpx" />
          <text>发消息给男方</text>
        </view>
        <view class="msg-btn flex-center" hover-class="hover-opacity" @tap="onMessageSide('female')">
          <app-icon name="message-primary" size="30rpx" />
          <text>发消息给女方</text>
        </view>
      </view>

      <view style="height: 60rpx" />
    </template>

    <!-- 空态 -->
    <template v-else-if="!loading">
      <view class="empty-state flex-center">
        <view class="empty-icon flex-center">
          <app-icon name="search-ink" size="56rpx" />
        </view>
        <text class="empty-title">未找到牵线记录</text>
        <text class="empty-sub">该牵线可能已不存在，或网络异常</text>
        <view class="empty-btn flex-center" hover-class="hover-opacity" @tap="goBack">返回上一页</view>
      </view>
    </template>
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
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
}
.glass-strong {
  position: relative;
  z-index: 2;
  margin: 24rpx 40rpx 0;
  border-radius: 40rpx;
  padding: 32rpx;
  background: var(--q-glass-bg-strong);
  border: 1px solid var(--q-glass-border);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  box-shadow: 0 24rpx 60rpx -28rpx rgba(120, 70, 80, 0.18);
}
.glass-soft {
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  border-radius: 28rpx;
}

/* ---------- 状态横幅 ---------- */
.banner-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: var(--q-gold-soft);
  color: var(--q-gold-dark);
  font-size: 26rpx;
  font-weight: 700;
}
.ps-term {
  background: var(--q-error-soft);
  color: var(--q-error);
}
.pulse {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: var(--q-gold-dark);
  box-shadow: 0 0 0 6rpx rgba(212, 175, 55, 0.25);
}
.banner-meta {
  text-align: right;
  display: flex;
  flex-direction: column;
}
.match-id {
  font-size: 22rpx;
  color: var(--q-ink-3);
}
.match-date {
  font-size: 22rpx;
  color: var(--q-ink-4);
  margin-top: 4rpx;
}

/* ---------- 双方 + 匹配度 ---------- */
.duo-hero {
  position: relative;
  z-index: 2;
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.duo-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  width: 30%;
}
.duo-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  font-size: 48rpx;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.9);
  box-shadow: 0 12rpx 32rpx rgba(120, 70, 80, 0.2);
}
.duo-name {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--q-ink-1);
  max-width: 180rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.duo-sub {
  font-size: 22rpx;
  color: var(--q-ink-3);
}
.ring-zone {
  position: relative;
  width: 140rpx;
  height: 140rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ring-pct {
  position: absolute;
  top: 52rpx;
  font-size: 36rpx;
  font-weight: 700;
  color: var(--q-gold-dark);
}
.ring-cap {
  position: absolute;
  bottom: 30rpx;
  font-size: 20rpx;
  color: var(--q-ink-3);
}

/* ---------- 进度步进 ---------- */
.steps {
  padding: 32rpx 28rpx;
}
.step-term-tip {
  text-align: center;
  font-size: 26rpx;
  color: var(--q-error);
  font-weight: 600;
}
.steps-row {
  display: flex;
  align-items: flex-start;
}
.step {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.step-dot {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  background: var(--q-glass-bg);
  border: 2rpx solid var(--q-glass-border);
  color: var(--q-ink-3);
  z-index: 2;
}
.step.done .step-dot {
  background: var(--q-primary);
  border-color: var(--q-primary);
  color: #fff;
}
.step.active .step-dot {
  background: var(--q-gold-gradient);
  border-color: var(--q-gold);
  color: #fff;
  box-shadow: 0 0 0 6rpx rgba(212, 175, 55, 0.2);
}
.step-check {
  font-size: 30rpx;
}
.step-label {
  font-size: 22rpx;
  color: var(--q-ink-3);
  text-align: center;
}
.step.active .step-label,
.step.done .step-label {
  color: var(--q-ink-1);
  font-weight: 600;
}
.step-line {
  position: absolute;
  top: 28rpx;
  left: 50%;
  width: 100%;
  height: 4rpx;
  background: var(--q-glass-border);
  z-index: 1;
}
.step-line.filled {
  background: var(--q-primary);
}

/* ---------- 区块标题 ---------- */
.section-title {
  margin: 40rpx 40rpx 0;
}
.card-title-text {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}

/* ---------- 双方资料 ---------- */
.profiles {
  position: relative;
  z-index: 2;
  margin: 20rpx 40rpx 0;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.pf-head {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 24rpx;
}
.pf-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  font-size: 40rpx;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.9);
}
.pf-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.pf-gender {
  font-size: 24rpx;
  color: var(--q-primary);
  font-weight: 500;
}
.pf-sub {
  display: block;
  font-size: 24rpx;
  color: var(--q-ink-3);
  margin-top: 4rpx;
}
.pf-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx 24rpx;
}
.pf-label {
  display: block;
  font-size: 22rpx;
  color: var(--q-ink-3);
}
.pf-value {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--q-ink-1);
  margin-top: 2rpx;
}
.pf-empty {
  text-align: center;
  font-size: 24rpx;
  color: var(--q-ink-4);
  padding: 40rpx;
}

/* ---------- 反馈 ---------- */
.reason-box {
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: var(--q-gold-soft);
  border: 1px solid rgba(212, 175, 55, 0.25);
  margin-bottom: 16rpx;
}
.reason-text {
  font-size: 26rpx;
  color: var(--q-gold-dark);
  line-height: 1.6;
}
.fb-row {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 0;
  border-top: 1px solid rgba(139, 37, 74, 0.06);
}
.fb-row:first-child {
  border-top: none;
}
.fb-side {
  flex-shrink: 0;
  width: 72rpx;
  height: 48rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 600;
}
.fb-male {
  background: rgba(106, 127, 181, 0.15);
  color: #4a5d8a;
}
.fb-female {
  background: rgba(199, 107, 130, 0.15);
  color: #b45470;
}
.fb-body {
  flex: 1;
  min-width: 0;
}
.fb-kind {
  display: inline-block;
  font-size: 22rpx;
  font-weight: 600;
  color: var(--q-primary);
  margin-right: 10rpx;
}
.fb-text {
  font-size: 26rpx;
  color: var(--q-ink-2);
  line-height: 1.6;
}
.fb-empty {
  text-align: center;
  font-size: 24rpx;
  color: var(--q-ink-4);
  padding: 16rpx;
}

/* ---------- 约见 ---------- */
.add-btn {
  gap: 8rpx;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 24rpx;
  font-weight: 600;
}
.meet-form {
  position: relative;
  z-index: 2;
  margin: 16rpx 40rpx 0;
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}
.mf-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.mf-label {
  width: 80rpx;
  font-size: 24rpx;
  color: var(--q-ink-3);
  flex-shrink: 0;
}
.mf-picker {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: #fff;
  font-size: 26rpx;
  color: var(--q-ink-1);
  display: flex;
  align-items: center;
}
.mf-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: #fff;
  font-size: 26rpx;
}
.mf-submit {
  height: 80rpx;
  border-radius: 20rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}
.mf-submit.disabled {
  opacity: 0.6;
}
.events {
  position: relative;
  z-index: 2;
  margin: 20rpx 40rpx 0;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.ev-row {
  display: flex;
  gap: 18rpx;
  padding: 24rpx;
}
.ev-dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  margin-top: 10rpx;
  flex-shrink: 0;
  background: var(--q-primary);
  box-shadow: 0 0 0 6rpx var(--q-primary-soft);
}
.ev-dot.ev-done {
  background: var(--q-success);
  box-shadow: 0 0 0 6rpx var(--q-success-soft);
}
.ev-dot.ev-cancel {
  background: var(--q-error);
  box-shadow: 0 0 0 6rpx var(--q-error-soft);
}
.ev-body {
  flex: 1;
  min-width: 0;
}
.ev-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.ev-status {
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: var(--q-primary-soft);
  color: var(--q-primary);
}
.ev-status.ev-done {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.ev-status.ev-cancel {
  background: var(--q-error-soft);
  color: var(--q-error);
}
.ev-meta {
  display: block;
  font-size: 24rpx;
  color: var(--q-ink-3);
  margin-top: 8rpx;
}
.ev-empty {
  text-align: center;
  font-size: 24rpx;
  color: var(--q-ink-4);
  padding: 24rpx;
}

/* ---------- 操作行 ---------- */
.op-bar {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 16rpx;
  margin: 40rpx 40rpx 0;
}
.op-btn {
  flex: 1;
  height: 84rpx;
  border-radius: 20rpx;
  font-size: 26rpx;
  font-weight: 600;
}
.op-call {
  background: rgba(139, 37, 74, 0.06);
  color: var(--q-ink-2);
}
.op-schedule {
  background: rgba(139, 37, 74, 0.06);
  color: var(--q-ink-2);
}
.op-done {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.op-fail {
  background: var(--q-error-soft);
  color: var(--q-error);
}
.op-disabled {
  opacity: 0.35;
  pointer-events: none;
}
.op-reco {
  box-shadow: 0 0 0 4rpx var(--q-gold), 0 10rpx 24rpx rgba(212, 175, 55, 0.3);
}
.msg-row {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 16rpx;
  margin: 20rpx 40rpx 0;
}
.msg-btn {
  flex: 1;
  height: 80rpx;
  gap: 10rpx;
  border-radius: 20rpx;
  background: var(--q-glass-bg-strong);
  border: 1px solid var(--q-glass-border);
  font-size: 26rpx;
  font-weight: 600;
  color: var(--q-primary);
}

/* ---------- 空态 ---------- */
.empty-state {
  position: relative;
  z-index: 2;
  flex-direction: column;
  gap: 20rpx;
  padding: 160rpx 60rpx;
  text-align: center;
}
.empty-icon {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  background: var(--q-primary-soft);
  border: 2rpx solid rgba(139, 37, 74, 0.12);
  margin-bottom: 8rpx;
}
.empty-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.empty-sub {
  font-size: 26rpx;
  color: var(--q-ink-3);
}
.empty-btn {
  margin-top: 16rpx;
  padding: 20rpx 48rpx;
  border-radius: 999rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  box-shadow: 0 10rpx 28rpx rgba(139, 37, 74, 0.25);
}

.hover-opacity {
  opacity: 0.6;
}
.hover-scale {
  transform: scale(1.05);
  transition: transform 0.15s ease;
}

/* ---------- 反馈录入表单（复用 meet-form 容器） ---------- */
.fu-textarea {
  height: 160rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #fff;
  font-size: 26rpx;
  line-height: 1.5;
  width: 100%;
  box-sizing: border-box;
}
.sel-pill {
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  font-size: 24rpx;
  color: var(--q-ink-2);
}
.sel-active {
  background: var(--q-primary);
  border-color: var(--q-primary);
  color: #fff;
  font-weight: 600;
}
</style>
