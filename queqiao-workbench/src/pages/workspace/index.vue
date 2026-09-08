<script setup lang="ts">
/**
 * 核心工作台（首页）— 数据来自 GET /api/dashboard
 * 模块：毛玻璃问候栏 / AI 智能录入悬浮胶囊 / KPI 概览（横向滑动）
 *       AI 最优推荐 / 今日待办 / 最新入会会员
 */
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { api, getStoredUser, type Dashboard, type Member } from '@/api'

/* ---------- 状态栏高度（自定义导航沉浸式） ---------- */
const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

/* ---------- 问候语 ---------- */
const me = getStoredUser()
const matchmakerName = ref(me?.name || '红娘')
const roleLabel = me?.role === 'ADMIN' ? '管理员' : me?.role === 'MANAGER' ? '主管' : '金牌红娘'
const hour = new Date().getHours()
const greeting = hour < 6 ? '夜深了' : hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'

/* ---------- KPI 图标映射 ---------- */
const ICON_MAP: Record<string, string> = {
  users: 'users-primary',
  heart: 'heart-white',
  dollar: 'file-gold',
  'file-text': 'file-ink',
}
const kpiIcon = (i: string) => ICON_MAP[i] || 'stat-match'
const isHeart = (i: string) => i === 'heart'

/* ---------- 数据 ---------- */
const loading = ref(false)
const offline = ref(false)
const data = ref<Dashboard | null>(null)

const kpis = () => data.value?.kpis ?? []
const recs = () => data.value?.recs ?? []
const todos = () => data.value?.todos ?? []
const members = () => data.value?.recentMembers ?? []

/** 空库判定：KPI「总会员数」为 0 —— 此时展示起步引导而非一堆 0 值卡片 */
const isEmptyData = computed(() => {
  const k = kpis().find((x) => (x.label || '').includes('会员'))
  const n = parseInt(String(k?.value ?? '0').replace(/\D/g, ''), 10)
  return Number.isFinite(n) && n === 0
})

const fetchDashboard = async () => {
  loading.value = true
  try {
    const res = await api.dashboard()
    data.value = res
    offline.value = false
  } catch (e: any) {
    // 失败不展示假数据：置空并提示离线，提供重试入口
    data.value = null
    offline.value = true
    uni.showToast({ title: (e && e.message) || '工作台加载失败', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh?.()
  }
}
const onRetry = () => {
  offline.value = false
  fetchDashboard()
}
onShow(fetchDashboard)
onPullDownRefresh(fetchDashboard)

/* ---------- 工具 ---------- */
const tagList = (tags?: string) => (tags ? tags.split(/[,，、]/).filter(Boolean) : [])
const ageText = (m: Member) => `${m.age ?? '-'}岁 · ${m.job || m.city || ''}`

/* ---------- 交互 ---------- */
const onBell = () => uni.switchTab({ url: '/pages/message/index' })
/** ai=1 时录入页自动弹出 AI 解析框，与「手动录入」形成真实区分 */
const onAiCapsule = () => uni.navigateTo({ url: '/pages/member/input?ai=1' })
const onManualCreate = () => uni.navigateTo({ url: '/pages/member/input' })
const onMemberTap = (m: Member) => uni.navigateTo({ url: `/pages/member/detail?id=${m.id}` })
const onFollow = (m: Member) => uni.navigateTo({ url: `/pages/member/detail?id=${m.id}` })
const onRecTap = (r: Dashboard['recs'][number]) =>
  uni.navigateTo({ url: `/pages/match/index?memberId=${r.forMemberId}&from=rec` })
const onTodoTap = (t: Dashboard['todos'][number]) => {
  const link = t.link || ''
  const pageMap: Record<string, string> = {
    '/calendar': '/pages/schedule/index',
    '/match': '/pages/match/index',
    '/members': '/pages/members/index',
    '/crm': '/pages/members/index',
    '/audit': '/pages/members/index',
  }
  const target = pageMap[link]
  if (target === '/pages/match/index' || target === '/pages/members/index') {
    uni.switchTab({ url: target })
  } else if (target) {
    uni.navigateTo({ url: target })
  }
}

/* ---------- 快捷操作（四大高频动作，对齐设计稿 Quick actions） ---------- */
const quickActions = [
  { label: '新建会员', icon: 'act-user-plus', url: '/pages/member/input' },
  { label: '快速牵线', icon: 'act-heart', url: '/pages/match/index', tab: true },
  { label: '约见安排', icon: 'act-calendar-check', url: '/pages/schedule/index' },
  { label: '跟进看板', icon: 'act-chart', url: '/pages/followup/kanban' },
]
const onQuick = (a: (typeof quickActions)[number]) => {
  if (a.tab) uni.switchTab({ url: a.url })
  else uni.navigateTo({ url: a.url })
}
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="loading" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 离线角标（失败不展示假数据） -->
    <view v-if="offline" class="offline-bar flex items-center" hover-class="hover-opacity" @tap="onRetry">
      <app-icon name="alert-warning" size="28rpx" />
      <text class="offline-text">数据离线 · 点击重试</text>
    </view>

    <!-- 顶部：毛玻璃问候栏（大字号排版） -->
    <view class="glass-bar greeting-bar">
      <view class="flex" style="flex-direction: column; gap: 4rpx">
        <text class="greeting-text">{{ greeting }}，{{ matchmakerName }}</text>
        <text class="greeting-role">{{ roleLabel }}</text>
      </view>
      <view class="bell-btn flex-center" hover-class="hover-scale" @tap="onBell">
        <app-icon name="bell" size="40rpx" />
        <view class="bell-dot" />
      </view>
    </view>

    <!-- AI 智能录入悬浮胶囊（香槟金渐变 + 微光晕 + 情感化脉冲） -->
    <view class="ai-capsule" hover-class="hover-lift" @tap="onAiCapsule">
      <view class="ai-glow anim-breathe" />
      <view class="ai-inner">
        <view class="mic-disc flex-center">
          <view class="mic-pulse" />
          <app-icon name="mic" size="48rpx" />
        </view>
        <text class="ai-title">粘贴文本，AI 自动建档</text>
        <text class="ai-sub">自然语言一键录入新会员 · 支持语音转写</text>
      </view>
    </view>

    <!-- 快捷操作行（四大高频动作） -->
    <view class="quick-row">
      <view
        v-for="a in quickActions"
        :key="a.label"
        class="quick-item"
        hover-class="hover-scale"
        @tap="onQuick(a)"
      >
        <view class="quick-circle flex-center">
          <app-icon :name="a.icon" size="44rpx" />
        </view>
        <text class="quick-label">{{ a.label }}</text>
      </view>
    </view>

    <!-- 空库引导：系统刚启用、还没有任何会员时，给出明确的下一步动作 -->
    <glass-card
      v-if="!loading && !offline && isEmptyData"
      strong
      padding="40rpx 36rpx"
      shadow="normal"
      class="start-card"
    >
      <view class="start-icon flex-center">
        <app-icon name="act-user-plus" size="48rpx" />
      </view>
      <text class="start-title">还没有会员档案</text>
      <text class="start-sub">录入第一位会员后，系统即可为你生成智能牵线推荐、待办提醒与跟进看板。</text>
      <view class="start-actions">
        <view class="start-btn start-primary flex-center" hover-class="hover-scale" @tap="onAiCapsule">
          <app-icon name="sparkles-gold" size="30rpx" />
          <text class="start-btn-text">AI 智能建档</text>
        </view>
        <view class="start-btn start-plain flex-center" hover-class="hover-opacity" @tap="onManualCreate">
          <app-icon name="act-user-plus" size="30rpx" />
          <text class="start-btn-plain-text">手动录入</text>
        </view>
      </view>
    </glass-card>

    <!-- 数据概览（Bento 网格，首卡通栏大字号） -->
    <view class="section" v-if="kpis().length && !isEmptyData">
      <view class="section-title section-head">
        <text class="section-name">数据概览</text>
      </view>
      <view class="bento board-bento">
        <view
          v-for="(k, i) in kpis()"
          :key="k.label"
          class="bento-cell board-cell"
          :class="{ 'cell-hero': i === 0 }"
          hover-class="hover-lift-card"
        >
          <view class="board-icon">
            <app-icon :name="kpiIcon(k.icon)" size="36rpx" />
          </view>
          <text class="board-label">{{ k.label }}</text>
          <view class="flex items-baseline" style="gap: 8rpx">
            <text class="board-value" :class="{ 'display-hero': i === 0 }">{{ k.value }}</text>
            <text class="board-unit">{{ k.unit }}</text>
          </view>
          <view class="flex items-center" style="gap: 8rpx; margin-top: 14rpx">
            <view v-if="isHeart(k.icon)" class="heart-mini flex-center anim-heartbeat">
              <app-icon name="heart-white" size="18rpx" />
            </view>
            <text class="board-trend" :class="{ good: k.up, bad: !k.up }">{{ k.trend }}</text>
          </view>
          <view class="cell-shine" />
        </view>
      </view>
    </view>

    <!-- AI 最优推荐 -->
    <view class="section" v-if="recs().length">
      <view class="section-title section-head">
        <text class="section-name">AI 最优匹配推荐</text>
      </view>
      <view class="rec-list">
        <glass-card
          v-for="r in recs()"
          :key="r.memberId"
          strong
          padding="32rpx 36rpx"
          shadow="normal"
          class="rec-card"
          @tap="onRecTap(r)"
        >
          <view class="flex items-center">
            <view class="rec-avatar flex-center" :style="{ background: r.avatarBg }">{{ r.initial }}</view>
            <view class="member-info">
              <view class="flex items-center" style="gap: 12rpx; margin-bottom: 8rpx">
                <text class="member-name">{{ r.name }}</text>
                <view class="score-pill flex-center">
                  <text class="score-text">{{ r.score }}分</text>
                </view>
              </view>
              <text class="rec-detail">{{ r.detail }}</text>
              <text class="rec-reason">{{ r.reason }}</text>
            </view>
            <app-icon name="chevron-right" size="28rpx" />
          </view>
        </glass-card>
      </view>
    </view>

    <!-- 今日待办 -->
    <view class="section" v-if="todos().length">
      <view class="section-title section-head">
        <text class="section-name">今日待办</text>
      </view>
      <view class="todo-list">
        <view
          v-for="(t, i) in todos()"
          :key="i"
          class="todo-row"
          hover-class="hover-opacity"
          @tap="onTodoTap(t)"
        >
          <view class="todo-dot" :class="t.priority" />
          <text class="todo-text">{{ t.text }}</text>
          <app-icon name="chevron-right" size="26rpx" />
        </view>
      </view>
    </view>

    <!-- 最新入会会员 -->
    <view class="section" v-if="members().length">
      <view class="flex-between section-head">
        <view class="section-title">
          <text class="section-name">最新入会会员</text>
        </view>
        <view
          class="flex items-center"
          hover-class="hover-opacity"
          @tap="uni.switchTab({ url: '/pages/members/index' })"
        >
          <text class="more-text">查看全部</text>
          <app-icon name="chevron-right" size="28rpx" />
        </view>
      </view>

      <view class="member-list">
        <view v-for="m in members()" :key="m.id" class="stack-wrap" @tap="onMemberTap(m)">
          <view class="stack-layer" />
          <glass-card strong padding="32rpx 36rpx" shadow="strong" class="member-card">
            <view class="flex items-center">
              <view class="avatar-wrap">
                <view
                  class="member-avatar flex-center"
                  :style="{ background: m.avatarBg, color: '#fff' }"
                >{{ m.name[0] }}</view>
                <view v-if="m.verified" class="badge-shield flex-center">
                  <app-icon name="shield-white" size="20rpx" />
                </view>
              </view>
              <view class="member-info">
                <view class="flex items-baseline" style="gap: 12rpx; margin-bottom: 10rpx">
                  <text class="member-name">{{ m.name }}</text>
                  <text class="member-meta">{{ ageText(m) }}</text>
                </view>
                <view class="flex flex-wrap" style="gap: 10rpx; margin-bottom: 12rpx">
                  <text v-for="t in tagList(m.tags).slice(0, 3)" :key="t" class="tag-soft">{{ t }}</text>
                </view>
                <view class="flex items-center" style="gap: 8rpx">
                  <text class="idle-text">{{ m.lastActive || '待跟进' }}</text>
                </view>
              </view>
              <view
                class="follow-btn flex-center"
                hover-class="hover-scale"
                @tap.stop="onFollow(m)"
              >立即跟进</view>
            </view>
          </glass-card>
        </view>
      </view>
    </view>

    <view style="height: 60rpx" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: transparent;
}

/* ---------- 问候栏 ---------- */
.greeting-bar {
  position: relative;
  z-index: 2;
  width: 100%;
  padding: 24rpx 40rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-sizing: border-box;
}
.greeting-text {
  font-size: var(--q-type-title-lg);
  font-weight: 700;
  color: var(--q-ink-1);
  letter-spacing: -0.02em;
}
.greeting-role {
  font-size: var(--q-type-caption);
  color: var(--q-ink-3);
  font-weight: 500;
  letter-spacing: 0.04em;
}
.bell-btn {
  position: relative;
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}
.bell-dot {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  width: 14rpx;
  height: 14rpx;
  background: var(--q-error);
  border-radius: 50%;
  border: 3rpx solid rgba(253, 251, 247, 0.9);
}

/* ---------- AI 智能录入悬浮胶囊 ---------- */
.ai-capsule {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx;
  border-radius: 64rpx;
}
.ai-glow {
  position: absolute;
  inset: -8rpx;
  border-radius: 72rpx;
  background: var(--q-gold-gradient);
  opacity: 0.35;
  filter: blur(24rpx);
}
.ai-inner {
  position: relative;
  border-radius: 64rpx;
  padding: 44rpx 40rpx;
  background: linear-gradient(135deg, #E3C766 0%, #D4AF37 48%, #B8941F 100%);
  box-shadow: 0 24rpx 48rpx -16rpx rgba(168, 134, 30, 0.45), inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
}
.mic-disc {
  position: relative;
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.28);
  border: 2rpx solid rgba(255, 255, 255, 0.55);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  margin-bottom: 24rpx;
}
.mic-pulse {
  position: absolute;
  inset: -14rpx;
  border-radius: 50%;
  border: 3rpx solid rgba(255, 255, 255, 0.6);
  animation: q-breathe 2s ease-in-out infinite;
}
.ai-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2rpx 8rpx rgba(120, 90, 10, 0.35);
  margin-bottom: 8rpx;
}
.ai-sub {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.85);
}

/* ---------- 快捷操作行 ---------- */
.quick-row {
  position: relative;
  z-index: 2;
  margin: 44rpx 40rpx 0;
  display: flex;
  justify-content: space-between;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  width: 25%;
}
.quick-circle {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}
.quick-label {
  font-size: 22rpx;
  color: var(--q-ink-2);
  font-weight: 500;
}

/* ---------- 区块 ---------- */
/* ---------- 空库起步引导 ---------- */
.start-card {
  margin: 8rpx 40rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.start-icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: var(--q-primary-soft);
  border: 2rpx solid rgba(139, 37, 74, 0.12);
  margin-bottom: 24rpx;
}
.start-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--q-ink-1);
  margin-bottom: 12rpx;
}
.start-sub {
  display: block;
  font-size: 26rpx;
  color: var(--q-ink-3);
  line-height: 1.7;
  margin-bottom: 32rpx;
}
.start-actions {
  display: flex;
  gap: 20rpx;
  width: 100%;
}
.start-btn {
  flex: 1;
  gap: 10rpx;
  height: 92rpx;
  border-radius: 24rpx;
}
.start-primary {
  background: var(--q-gold-gradient);
  box-shadow: 0 12rpx 28rpx rgba(168, 134, 30, 0.32);
}
.start-btn-text {
  font-size: 28rpx;
  font-weight: 700;
  color: #fff;
}
.start-plain {
  background: rgba(150, 128, 137, 0.1);
  border: 2rpx solid rgba(139, 37, 74, 0.14);
}
.start-btn-plain-text {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--q-primary);
}

.section {
  position: relative;
  z-index: 2;
  margin-top: 44rpx;
}
.section-head {
  padding: 0 40rpx;
  margin-bottom: 28rpx;
}
.section-name {
  font-size: var(--q-type-title);
  font-weight: 700;
  color: var(--q-ink-1);
  letter-spacing: -0.01em;
}
.more-text {
  font-size: 26rpx;
  color: var(--q-ink-3);
}

/* ---------- Bento 数据概览 ---------- */
.board-bento {
  padding: 0 40rpx;
}
.board-cell {
  padding: 32rpx;
  min-height: 208rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.cell-hero {
  grid-column: 1 / -1;
  min-height: 240rpx;
  background:
    linear-gradient(135deg, rgba(139, 37, 74, 0.98) 0%, rgba(139, 37, 74, 0.9) 45%, rgba(201, 122, 150, 0.85) 100%);
  border-color: rgba(255, 255, 255, 0.35);
  box-shadow: 0 24rpx 56rpx -14rpx rgba(139, 37, 74, 0.4), var(--q-glass-hi-line);
}
.cell-shine {
  position: absolute;
  inset: 0;
  background: var(--q-glass-specular);
  pointer-events: none;
}
.cell-hero .board-icon {
  background: rgba(255, 255, 255, 0.22);
  border-radius: 50%;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cell-hero .board-label {
  color: rgba(255, 255, 255, 0.75);
}
.cell-hero .board-value {
  color: #fff;
  text-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.18);
}
.cell-hero .board-unit {
  color: rgba(255, 255, 255, 0.75);
}
.cell-hero .board-trend {
  color: rgba(255, 255, 255, 0.85);
}
.cell-hero .board-trend.good {
  color: #DFF0DD;
}
.cell-hero .board-trend.bad {
  color: #F6D9C0;
}
.board-icon {
  position: absolute;
  top: 28rpx;
  right: 28rpx;
}
.board-label {
  display: block;
  font-size: var(--q-type-caption);
  color: var(--q-ink-3);
  font-weight: 500;
}
.board-value {
  font-size: var(--q-type-hero);
  font-weight: 800;
  color: var(--q-gold-dark);
  line-height: 1;
  letter-spacing: -0.03em;
}
.board-value.display-hero {
  font-size: var(--q-type-display);
}
.board-unit {
  font-size: var(--q-type-body);
  color: var(--q-ink-2);
  font-weight: 500;
}
.board-trend {
  font-size: var(--q-type-caption);
  color: var(--q-ink-3);
}
.board-trend.good {
  color: var(--q-success);
  font-weight: 600;
}
.board-trend.bad {
  color: var(--q-warning);
}
.heart-mini {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: var(--q-gold-gradient);
}

/* ---------- AI 推荐 ---------- */
.rec-list {
  margin: 0 40rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.rec-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  color: #fff;
  font-size: 34rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.score-pill {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: var(--q-gold-soft);
}
.score-text {
  font-size: 22rpx;
  color: var(--q-gold-dark);
  font-weight: 700;
}
.rec-detail {
  display: block;
  font-size: 24rpx;
  color: var(--q-ink-2);
  margin-bottom: 8rpx;
}
.rec-reason {
  display: block;
  font-size: 22rpx;
  color: var(--q-ink-3);
}

/* ---------- 待办 ---------- */
.todo-list {
  margin: 0 40rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.todo-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx 32rpx;
  border-radius: var(--q-radius-lg);
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.6);
}
.todo-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  flex-shrink: 0;
}
.todo-dot.high {
  background: var(--q-error);
}
.todo-dot.medium {
  background: var(--q-warning);
}
.todo-dot.low {
  background: var(--q-ink-4);
}
.todo-text {
  flex: 1;
  font-size: 28rpx;
  color: var(--q-ink-1);
}

/* ---------- 最新会员 ---------- */
.member-list {
  margin: 0 40rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}
.stack-wrap {
  position: relative;
}
.stack-layer {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  bottom: -12rpx;
  height: 60%;
  border-radius: 56rpx;
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--q-glass-shadow-soft);
}
.member-card {
  position: relative;
}
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}
.member-avatar {
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  font-size: 36rpx;
  font-weight: 700;
  box-shadow: 0 8rpx 24rpx rgba(139, 37, 74, 0.1);
}
.badge-shield {
  position: absolute;
  bottom: -2rpx;
  right: -2rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #6FA06E 0%, #5B8C5A 100%);
  border: 4rpx solid #fff;
  box-shadow: 0 4rpx 12rpx rgba(91, 140, 90, 0.35);
}
.member-info {
  flex: 1;
  margin-left: 28rpx;
  min-width: 0;
}
.member-name {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--q-ink-1);
  letter-spacing: -0.01em;
}
.member-meta {
  font-size: 24rpx;
  color: var(--q-ink-3);
  font-weight: 500;
}
.idle-text {
  font-size: 24rpx;
  color: var(--q-warning);
  font-weight: 500;
}
.follow-btn {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 18rpx 28rpx;
  border-radius: 24rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: 0 8rpx 24rpx rgba(139, 37, 74, 0.25);
}

/* ---------- 触摸反馈 ---------- */
.hover-scale {
  transform: scale(1.05);
  transition: transform 0.15s ease;
}
.hover-lift {
  transform: translateY(-4rpx);
  transition: transform 0.2s ease;
}
.hover-opacity {
  opacity: 0.6;
}

/* ---------- 离线角标 ---------- */
.offline-bar {
  position: relative;
  z-index: 2;
  margin: 16rpx 40rpx 0;
  gap: 12rpx;
  padding: 16rpx 28rpx;
  border-radius: 999rpx;
  background: var(--q-warning-soft);
  border: 2rpx solid rgba(212, 175, 55, 0.3);
}
.offline-text {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--q-gold-dark);
}
</style>
