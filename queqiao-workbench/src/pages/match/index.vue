<script setup lang="ts">
/**
 * 智能牵线 — Tab 主页（严格按设计稿：AI推荐Banner/进度/卡片堆叠/三按钮/今日已牵线）
 * 数据：GET /api/match/recommendations（Python AI 打分）、GET /api/pairs
 */
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { api, type Member, type Pair } from '@/api'
import { toDisplay } from '@/utils/pairStatus'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

interface Reco {
  member: Member
  score: number
  reasons: string[]
}

// 目标会员：从会员库「牵线」带入 memberId；直接进 Tab 则无目标，等待选择
const targetId = ref<string>('')
const targetName = ref('')
const recos = ref<Reco[]>([])
const current = ref(0)
const todayPairs = ref<Pair[]>([])
const loading = ref(false)

const currentReco = computed<Reco | null>(() => recos.value[current.value] ?? null)
const progressPct = computed(() =>
  recos.value.length ? Math.round(((current.value + 1) / recos.value.length) * 100) : 0
)

const fetchData = async () => {
  if (!targetId.value) return
  loading.value = true
  try {
    const [r, p] = await Promise.all([
      api.recommendations(targetId.value),
      api.pairs(),
    ])
    targetName.value = r.target?.name || '会员'
    recos.value = r.list || []
    current.value = 0
    todayPairs.value = p.list.slice(0, 2)
  } catch {
    uni.showToast({ title: '后端未连接', icon: 'none' })
  } finally {
    loading.value = false
  }
}

onLoad((q) => {
  const id = String(q?.memberId || '')
  if (id) {
    targetId.value = id
    targetName.value = q?.memberName ? String(q.memberName) : ''
    uni.setNavigationBarTitle({ title: `为「${targetName.value || '会员'}」牵线` })
  }
  fetchData()
})
// Tab 切回时刷新（保留 memberId 上下文，不再硬编码为 2）
onShow(() => {
  loadShortlist()
  if (targetId.value) fetchData()
})
onPullDownRefresh(() => {
  if (targetId.value) fetchData()
})

const onSkip = () => {
  if (current.value < recos.value.length - 1) current.value++
  else uni.showToast({ title: '今日推荐已看完', icon: 'none' })
}
/** 头像底色：根据姓名确定性取色（列表接口不返回 avatarBg，本地生成保证稳定） */
const AVATAR_PALETTE = [
  'linear-gradient(135deg,#E3C766,#D4AF37)',
  'linear-gradient(135deg,#C8D8C0,#5B8C5A)',
  'linear-gradient(135deg,#F0C0C8,#C76B82)',
  'linear-gradient(135deg,#B8C6E0,#6A7FB5)',
  'linear-gradient(135deg,#E0B8A0,#B5784F)',
  'linear-gradient(135deg,#C0B8E0,#7A6AB5)',
]
const avatarColor = (name?: string) => {
  const s = name || ''
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  return AVATAR_PALETTE[h % AVATAR_PALETTE.length]
}
/** 候选清单（本地持久化，跨会话保留；去重按会员 id） */
const SHORTLIST_KEY = 'queqiao_shortlist'
interface ShortlistItem {
  id: string
  name: string
  gender?: string
  age?: number
  city?: string
  job?: string
  avatarBg?: string
  score?: number
  reason?: string
}
const shortlist = ref<ShortlistItem[]>([])
const shortlistOpen = ref(false)
const loadShortlist = () => {
  try {
    shortlist.value = uni.getStorageSync(SHORTLIST_KEY) || []
  } catch {
    shortlist.value = []
  }
}
const persistShortlist = () => {
  try {
    uni.setStorageSync(SHORTLIST_KEY, shortlist.value)
  } catch {
    /* 持久化失败不影响当次交互 */
  }
}
const inShortlist = (id: string) => shortlist.value.some((s) => s.id === id)
/** 加入候选：红娘把该推荐标记为意向对象，本地保留以便后续统一发起牵线 */
const onShortlist = () => {
  const r = currentReco.value
  if (!r) return
  const m = r.member
  if (inShortlist(m.id)) {
    uni.showToast({ title: `${m.name} 已在候选中`, icon: 'none' })
  } else {
    shortlist.value.push({
      id: m.id,
      name: m.name,
      gender: m.gender,
      age: m.age,
      city: m.city,
      job: m.job,
      avatarBg: m.avatarBg,
      score: r.score,
      reason: (r.reasons || [])[0] || '',
    })
    persistShortlist()
    uni.showToast({ title: `已加入候选（共 ${shortlist.value.length} 位）`, icon: 'none' })
  }
  onSkip()
}
const removeShortlist = (id: string) => {
  shortlist.value = shortlist.value.filter((s) => s.id !== id)
  persistShortlist()
}
const openShortlist = () => {
  loadShortlist()
  shortlistOpen.value = true
}
/** 从候选清单一键发起牵线（候选是 targetId 的推荐对象） */
const createPairFromShortlist = async (item: ShortlistItem) => {
  if (!targetId.value) {
    uni.showToast({ title: '请先从会员库选择目标会员', icon: 'none' })
    return
  }
  const maleId = item.gender === '男' ? item.id : targetId.value
  const femaleId = item.gender === '女' ? item.id : targetId.value
  try {
    await api.createPair({ maleId, femaleId })
    uni.showToast({ title: `已向 ${item.name} 发起牵线`, icon: 'success' })
    removeShortlist(item.id)
  } catch {
    uni.showToast({ title: '发起失败', icon: 'none' })
  }
}
const onMatch = async () => {
  const r = currentReco.value
  if (!r) return
  const maleId = r.member.gender === '男' ? r.member.id : targetId.value
  const femaleId = r.member.gender === '女' ? r.member.id : targetId.value
  try {
    await api.createPair({ maleId, femaleId })
    uni.showToast({ title: `已向 ${r.member.name} 发起牵线`, icon: 'success' })
  } catch {
    uni.showToast({ title: '发起失败', icon: 'none' })
  }
  onSkip()
}
const onRefresh = () => fetchData()
const goMembers = () => uni.switchTab({ url: '/pages/members/index' })

/* ---------- 牵手式拖拽滑卡（左滑暂不考虑 / 右滑加入候选） ---------- */
const dragX = ref(0)
const dragY = ref(0)
const dragging = ref(false)
const animating = ref(false)
let startX = 0
let startY = 0
let lockDir: '' | 'h' | 'v' = ''
const SWIPE_THRESHOLD = 90

const cardStyle = computed(() => {
  const rotate = dragX.value * 0.06
  return {
    transform: `translateX(${dragX.value}px) translateY(${dragY.value}px) rotate(${rotate}deg)`,
    transition: dragging.value || animating.value
      ? 'none'
      : 'transform 0.28s cubic-bezier(0.2, 0.8, 0.2, 1)',
  }
})
const shortlistOpacity = computed(() => (dragX.value > 0 ? Math.min(1, dragX.value / 120) : 0))
const skipOpacity = computed(() => (dragX.value < 0 ? Math.min(1, -dragX.value / 120) : 0))

const onCardTouchStart = (e: TouchEvent) => {
  if (animating.value) return
  startX = e.touches[0].clientX
  startY = e.touches[0].clientY
  dragging.value = true
  lockDir = ''
}
const onCardTouchMove = (e: TouchEvent) => {
  if (!dragging.value || animating.value) return
  const dx = e.touches[0].clientX - startX
  const dy = e.touches[0].clientY - startY
  if (!lockDir) lockDir = Math.abs(dx) > Math.abs(dy) ? 'h' : 'v'
  if (lockDir !== 'h') return
  dragX.value = dx
  dragY.value = dy
}
const onCardTouchEnd = () => {
  if (!dragging.value || animating.value) return
  dragging.value = false
  if (lockDir !== 'h') { dragX.value = 0; dragY.value = 0; return }
  if (dragX.value > SWIPE_THRESHOLD) flyOut(1)
  else if (dragX.value < -SWIPE_THRESHOLD) flyOut(-1)
  else { dragX.value = 0; dragY.value = 0 }
}
const flyOut = (dir: number) => {
  animating.value = true
  dragX.value = dir * 720
  setTimeout(() => {
    if (dir > 0) onShortlist()
    else onSkip()
    dragX.value = 0
    dragY.value = 0
    animating.value = false
    lockDir = ''
  }, 240)
}
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="loading" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 顶栏（大字号） -->
    <view class="glass-bar topbar">
      <text class="topbar-title">智能牵线</text>
      <view class="circle-btn flex-center" hover-class="hover-opacity" @tap="openShortlist">
        <app-icon name="history-ink" size="36rpx" />
        <view v-if="shortlist.length" class="sl-badge flex-center">{{ shortlist.length }}</view>
      </view>
    </view>

    <!-- 无目标会员（直接进 Tab）的引导空态 -->
    <view v-if="!targetId && !loading" class="empty-target flex-center">
      <text class="empty-target-text">请从「会员库」选择一位会员发起智能牵线</text>
      <view class="empty-target-btn flex-center" hover-class="hover-opacity" @tap="goMembers">去会员库</view>
    </view>

    <!-- AI 推荐 Banner（液态玻璃） -->
    <view class="ai-banner flex items-center">
      <view class="ai-icon flex-center">
        <app-icon name="sparkles-white" size="36rpx" />
      </view>
      <view class="flex-1">
        <text class="ai-title">今日 AI 推荐</text>
        <text class="ai-sub">基于128项维度，为你筛选出 <text class="ai-num">{{ recos.length }}</text> 位高匹配度会员</text>
      </view>
      <view class="refresh-btn flex-center" hover-class="hover-opacity" @tap="onRefresh">刷新</view>
    </view>

    <!-- 进度指示 -->
    <view class="progress-row flex-between" v-if="recos.length">
      <text class="progress-text">推荐 {{ current + 1 }} / {{ recos.length }}</text>
      <view class="progress-track">
        <view class="progress-fill" :style="{ width: progressPct + '%' }" />
      </view>
    </view>

    <!-- 卡片堆叠 -->
    <view class="stack-zone" v-if="currentReco">
      <view class="back-layer layer-2" />
      <view class="back-layer layer-1" />
      <view
        class="main-card"
        :style="cardStyle"
        @touchstart="onCardTouchStart"
        @touchmove.prevent="onCardTouchMove"
        @touchend="onCardTouchEnd"
        @touchcancel="onCardTouchEnd"
      >
        <!-- 滑卡角标（拖动时浮现） -->
        <view class="swipe-badge badge-shortlist" :style="{ opacity: shortlistOpacity }">加入候选</view>
        <view class="swipe-badge badge-skip" :style="{ opacity: skipOpacity }">暂不考虑</view>
        <!-- 照片区 -->
        <view class="photo-area" :style="{ background: currentReco.member.avatarBg }">
          <text class="photo-char">{{ currentReco.member.name[0] }}</text>
          <!-- 认证胶囊 -->
          <view class="photo-certs">
            <view v-if="currentReco.member.verified" class="photo-cert flex items-center">
              <app-icon name="shield-white" size="22rpx" />
              <text>已实名认证</text>
            </view>
            <view v-if="currentReco.member.certs?.includes('学历认证')" class="photo-cert flex items-center">
              <app-icon name="graduation-white" size="22rpx" />
              <text>已学历认证</text>
            </view>
          </view>
          <!-- 匹配度环 -->
          <view class="photo-ring">
            <progress-ring :percent="currentReco.score" size="104rpx" stroke="10rpx" />
            <text class="ring-caption">匹配度</text>
          </view>
          <!-- 姓名浮层 -->
          <view class="photo-name flex items-baseline">
            <text class="pn-name">{{ currentReco.member.name }}</text>
            <text class="pn-age">· {{ currentReco.member.age }}岁</text>
            <text class="pn-gender">{{ currentReco.member.gender === '女' ? '♀' : '♂' }}</text>
          </view>
        </view>
        <!-- 信息区 -->
        <view class="info-area">
          <view class="flex flex-wrap" style="gap: 16rpx; margin-bottom: 16rpx">
            <text class="info-tag">{{ currentReco.member.city }}</text>
            <text class="info-tag">{{ currentReco.member.job }}</text>
            <text class="info-tag">{{ currentReco.member.education }}</text>
            <text v-if="currentReco.member.height" class="info-tag">{{ currentReco.member.height }}cm</text>
          </view>
          <text class="info-intro">{{ currentReco.member.intro }}</text>
          <!-- 匹配亮点 -->
          <view class="hl-list">
            <view v-for="h in currentReco.reasons" :key="h" class="hl-item flex items-center">
              <app-icon name="sparkles-gold" size="20rpx" />
              <text>{{ h }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 三按钮操作区 -->
    <view class="actions flex-center" v-if="currentReco">
      <view class="act-circle act-skip flex-center" hover-class="hover-scale" @tap="onSkip">
        <app-icon name="x-ink" size="44rpx" />
      </view>
      <view class="act-circle act-main flex-center anim-heartbeat" hover-class="hover-scale" @tap="onMatch">
        <app-icon name="handshake-white" size="56rpx" />
      </view>
      <view class="act-circle act-shortlist flex-center" hover-class="hover-scale" @tap="onShortlist">
        <app-icon name="plus-white" size="44rpx" />
      </view>
    </view>
    <text class="act-tip" v-if="currentReco">点击大按钮发起牵线</text>

    <!-- 今日已牵线 -->
    <glass-card v-if="todayPairs.length" padding="32rpx" radius="40rpx" class="today-card">
      <view class="flex items-center" style="gap: 12rpx">
        <app-icon name="check-circle-success" size="32rpx" />
        <text class="today-title">今日已牵线</text>
      </view>
      <view class="today-divider" />
      <view v-for="p in todayPairs" :key="p.id" class="today-row flex-between">
        <view class="flex items-center" style="gap: 16rpx">
          <view class="duo-wrap">
            <view class="duo d-1 flex-center" :style="{ background: avatarColor(p.femaleName) }">{{ (p.femaleName || '女嘉宾')[0] }}</view>
            <view class="duo d-2 flex-center" :style="{ background: avatarColor(p.maleName) }">{{ (p.maleName || '男嘉宾')[0] }}</view>
          </view>
          <text class="today-names">{{ p.femaleName || '女嘉宾' }} × {{ p.maleName || '男嘉宾' }}</text>
        </view>
        <text class="today-status" :class="toDisplay(p.status) === '已约见' || toDisplay(p.status) === '已脱单' ? 'ts-success' : 'ts-warning'">{{ p.statusLabel || p.status }}</text>
      </view>
    </glass-card>

    <view style="height: 60rpx" />
  </view>

  <!-- 候选抽屉 -->
  <view v-if="shortlistOpen" class="sl-mask" @tap="shortlistOpen = false">
    <view class="sl-sheet" @tap.stop>
      <view class="sl-head flex-between">
        <text class="sl-title">候选清单（{{ shortlist.length }}）</text>
        <view class="sl-close flex-center" hover-class="hover-opacity" @tap="shortlistOpen = false">
          <app-icon name="close-ink" size="32rpx" />
        </view>
      </view>

      <view v-if="shortlist.length === 0" class="sl-empty">还没有候选，右滑或点「+」把心仪推荐加入候选</view>

      <scroll-view v-else scroll-y class="sl-scroll">
        <view v-for="s in shortlist" :key="s.id" class="sl-item">
          <view class="sl-avatar flex-center" :style="{ background: s.avatarBg || 'linear-gradient(135deg,#E8C7D4,#8B254A)', color: '#fff' }">{{ s.name[0] }}</view>
          <view class="sl-info" style="min-width: 0">
            <text class="sl-name">{{ s.name }}<text class="sl-meta"> · {{ s.age || '—' }}岁 · {{ s.city || '—' }}</text></text>
            <text class="sl-reason" v-if="s.reason">{{ s.reason }}</text>
          </view>
          <view class="sl-ops">
            <view class="sl-op sl-go flex-center" hover-class="hover-opacity" @tap="createPairFromShortlist(s)">牵线</view>
            <view class="sl-op sl-del flex-center" hover-class="hover-opacity" @tap="removeShortlist(s.id)">移除</view>
          </view>
        </view>
      </scroll-view>
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
  font-size: var(--q-type-title-lg);
  font-weight: 700;
  color: var(--q-ink-1);
  letter-spacing: -0.02em;
}
.circle-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}

/* ---------- AI Banner ---------- */
.ai-banner {
  position: relative;
  z-index: 2;
  margin: 24rpx 40rpx 0;
  border-radius: 40rpx;
  padding: 28rpx 32rpx;
  gap: 24rpx;
  background: linear-gradient(135deg, rgba(139, 37, 74, 0.06), rgba(212, 175, 55, 0.1));
  border: 1px solid rgba(139, 37, 74, 0.1);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
}
.ai-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--q-gold-gradient);
  flex-shrink: 0;
}
.ai-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.ai-sub {
  display: block;
  font-size: 24rpx;
  color: var(--q-ink-3);
  margin-top: 4rpx;
}
.ai-num {
  color: var(--q-primary);
  font-weight: 700;
}
.refresh-btn {
  flex-shrink: 0;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}
.flex-1 {
  flex: 1;
  min-width: 0;
}

/* ---------- 进度 ---------- */
.progress-row {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
  align-items: center;
}
.progress-text {
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.progress-track {
  width: 240rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: var(--q-primary-soft);
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--q-gold-gradient);
  border-radius: 999rpx;
  transition: width 0.3s ease;
}

/* ---------- 卡片堆叠 ---------- */
.stack-zone {
  position: relative;
  z-index: 2;
  margin: 40rpx;
  min-height: 760rpx;
}
.back-layer {
  position: absolute;
  inset: 0;
  border-radius: 56rpx;
  border: 1px solid var(--q-glass-border);
}
.layer-2 {
  transform: scale(0.96);
  background: var(--q-glass-bg-light);
}
.layer-1 {
  transform: scale(0.98);
  background: var(--q-glass-bg);
}
.main-card {
  position: relative;
  border-radius: 56rpx;
  background: var(--q-glass-bg-strong);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border: 1px solid var(--q-glass-border);
  box-shadow: 0 40rpx 80rpx -24rpx rgba(120, 70, 80, 0.15);
  overflow: hidden;
}

/* ---------- 滑卡角标（拖动时浮现） ---------- */
.swipe-badge {
  position: absolute;
  top: 40rpx;
  z-index: 6;
  padding: 12rpx 28rpx;
  border-radius: 16rpx;
  border: 4rpx solid;
  font-size: 40rpx;
  font-weight: 700;
  letter-spacing: 0.06em;
  pointer-events: none;
  will-change: opacity;
}
.badge-shortlist {
  right: 40rpx;
  color: #a87a52;
  border-color: #a87a52;
  transform: rotate(-14deg);
  background: rgba(168, 122, 82, 0.08);
}
.badge-skip {
  left: 40rpx;
  color: #c0392b;
  border-color: #c0392b;
  transform: rotate(14deg);
  background: rgba(192, 57, 43, 0.08);
}

.photo-area {
  position: relative;
  height: 440rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.photo-char {
  font-size: 144rpx;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.5);
}
.photo-certs {
  position: absolute;
  top: 24rpx;
  left: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.photo-cert {
  gap: 8rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.35);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  font-size: 20rpx;
  color: #fff;
}
.photo-ring {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.ring-caption {
  font-size: 22rpx;
  color: #fff;
  text-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.2);
}
.photo-name {
  position: absolute;
  bottom: 28rpx;
  left: 32rpx;
  gap: 12rpx;
}
.pn-name {
  font-size: 48rpx;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.25);
}
.pn-age {
  font-size: 36rpx;
  color: rgba(255, 255, 255, 0.8);
}
.pn-gender {
  font-size: 36rpx;
  color: #fff;
}
.info-area {
  padding: 32rpx 36rpx;
}
.info-tag {
  padding: 8rpx 20rpx;
  border-radius: 16rpx;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 24rpx;
}
.info-intro {
  font-size: 26rpx;
  color: var(--q-ink-2);
  line-height: 1.6;
}
.hl-list {
  margin-top: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.hl-item {
  gap: 10rpx;
  padding: 6rpx 20rpx;
  border-left: 4rpx solid var(--q-gold);
  font-size: 22rpx;
  color: var(--q-ink-2);
}

/* ---------- 三按钮 ---------- */
.actions {
  position: relative;
  z-index: 2;
  gap: 32rpx;
  margin-top: 8rpx;
}
.act-circle {
  border-radius: 50%;
  transition: transform 0.15s ease;
}
.act-skip {
  width: 104rpx;
  height: 104rpx;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
}
.act-main {
  width: 128rpx;
  height: 128rpx;
  background: var(--q-primary);
  border: 6rpx solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 16rpx 48rpx rgba(139, 37, 74, 0.35);
}
.act-shortlist {
  width: 104rpx;
  height: 104rpx;
  background: var(--q-gold-gradient);
  box-shadow: 0 12rpx 32rpx rgba(168, 134, 30, 0.3);
}
.act-tip {
  position: relative;
  z-index: 2;
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: var(--q-ink-3);
  margin-top: 20rpx;
}

/* ---------- 今日已牵线 ---------- */
.today-card {
  position: relative;
  z-index: 2;
  margin: 40rpx;
}
.today-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.today-divider {
  height: 1px;
  background: rgba(139, 37, 74, 0.08);
  margin: 20rpx 0;
}
.today-row {
  padding: 12rpx 0;
  align-items: center;
}
.duo-wrap {
  position: relative;
  width: 84rpx;
  height: 64rpx;
}
.duo {
  position: absolute;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  font-size: 24rpx;
  font-weight: 700;
  color: #fff;
  border: 4rpx solid #fff;
}
.d-1 {
  left: 0;
  z-index: 1;
}
.d-2 {
  right: 0;
}
.today-names {
  font-size: 26rpx;
  color: var(--q-ink-2);
}
.today-status {
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
}
.ts-success {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.ts-warning {
  background: var(--q-warning-soft);
  color: var(--q-warning);
}

.hover-opacity {
  opacity: 0.6;
}
.empty-target {
  position: relative;
  z-index: 2;
  margin: 40rpx;
  flex-direction: column;
  gap: 24rpx;
  padding: 80rpx 40rpx;
  text-align: center;
}
.empty-target-text {
  font-size: 28rpx;
  color: var(--q-ink-3);
}
.empty-target-btn {
  padding: 20rpx 48rpx;
  border-radius: 999rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}
.hover-scale {
  transform: scale(1.08);
  transition: transform 0.15s ease;
}

/* ---------- 候选角标 ---------- */
.sl-badge {
  position: absolute;
  top: -6rpx;
  right: -6rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 6rpx;
  border-radius: 16rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
  border: 3rpx solid var(--q-bg-base);
  box-sizing: border-box;
}

/* ---------- 候选抽屉 ---------- */
.sl-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(40, 20, 30, 0.4);
  display: flex;
  align-items: flex-end;
}
.sl-sheet {
  width: 100%;
  max-height: 72vh;
  background: var(--q-bg-base);
  border-radius: 40rpx 40rpx 0 0;
  padding: 28rpx 32rpx calc(28rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.sl-head {
  align-items: center;
  margin-bottom: 16rpx;
}
.sl-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.sl-close {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
}
.sl-empty {
  text-align: center;
  font-size: 26rpx;
  color: var(--q-ink-3);
  padding: 80rpx 40rpx;
  line-height: 1.6;
}
.sl-scroll {
  max-height: 56vh;
}
.sl-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0;
  border-bottom: 1px solid rgba(139, 37, 74, 0.06);
}
.sl-avatar {
  width: 84rpx;
  height: 84rpx;
  border-radius: 50%;
  font-size: 32rpx;
  font-weight: 300;
  flex-shrink: 0;
}
.sl-info {
  flex: 1;
}
.sl-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.sl-meta {
  font-size: 22rpx;
  color: var(--q-ink-3);
  font-weight: 400;
}
.sl-reason {
  display: block;
  font-size: 22rpx;
  color: var(--q-ink-3);
  margin-top: 6rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sl-ops {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  flex-shrink: 0;
}
.sl-op {
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
}
.sl-go {
  background: var(--q-primary);
  color: #fff;
}
.sl-del {
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  color: var(--q-ink-3);
}
</style>
