<script setup lang="ts">
/**
 * 会员库 — Tab 主页
 *
 * 筛选契约（已逐项实测后端，仅保留真实生效的参数，杜绝「点了没反应」）：
 *  ✅ 生效：keyword / gender / city / education / level / status / agent / page / size
 *  ❌ 后端不支持：minAge / maxAge / verified / agentId / 各种 sort
 *      —— 因此年龄区间、是否已认证、身高这类筛选不在前端伪造，避免给出错误结果。
 *
 * 「我负责」传 agent = 当前登录用户**姓名**（实测传 username / userId 均不生效）。
 * 统计行数据来自 GET /api/members/stats（真实值，非写死）。
 */
import { onShow, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { api, getStoredUser, type Member } from '@/api'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

/* ---------- 当前登录人（用于「我负责」筛选） ---------- */
const me = getStoredUser()
const meName = ref(me?.name || '')

/* ---------- 筛选态 ---------- */
const keyword = ref('')
const scope = ref<'all' | 'mine'>('all')
/** 快捷 chips：全部为后端实测生效的维度 */
const chips = [
  { label: '全部', params: {} as Record<string, any> },
  { label: '女性', params: { gender: '女' } },
  { label: '男性', params: { gender: '男' } },
  { label: 'A级', params: { level: 'A' } },
  { label: 'S级', params: { level: 'S' } },
  { label: '本科', params: { education: '本科' } },
  { label: '硕士', params: { education: '硕士' } },
]
const activeChip = ref('全部')

/** 高级筛选（城市 / 学历，均为后端实测生效参数） */
const showFilter = ref(false)
const fCity = ref('')
const fEducation = ref('')
const fLevel = ref('')
const EDU_OPTIONS = ['不限', '大专', '本科', '硕士', '博士']
const LEVEL_OPTIONS = ['不限', 'A', 'S']
const filterActive = () => !!(fCity.value || fEducation.value || fLevel.value)

/* ---------- 列表 ---------- */
const list = ref<Member[]>([])
const total = ref(0)
const stats = ref<{ total: number; newThisMonth: number; male: number; female: number } | null>(null)
const loading = ref(false)
const firstLoaded = ref(false)
const page = ref(1)
const pageSize = 20
const finished = ref(false)

const buildParams = () => {
  const chip = chips.find((c) => c.label === activeChip.value)
  const p: Record<string, any> = { ...(chip?.params || {}) }
  if (keyword.value) p.keyword = keyword.value
  if (scope.value === 'mine' && meName.value) p.agent = meName.value
  if (fCity.value) p.city = fCity.value
  if (fEducation.value) p.education = fEducation.value
  if (fLevel.value) p.level = fLevel.value
  p.page = page.value
  p.size = pageSize
  return p
}

const loadPage = async (reset: boolean) => {
  if (reset) {
    page.value = 1
    finished.value = false
  }
  loading.value = true
  try {
    const res = await api.members(buildParams())
    const items = res.list || []
    total.value = res.total ?? items.length
    list.value = reset ? items : [...list.value, ...items]
    if (items.length < pageSize) finished.value = true
  } catch {
    if (reset) list.value = []
    total.value = 0
    uni.showToast({ title: '后端未连接', icon: 'none' })
  } finally {
    loading.value = false
    firstLoaded.value = true
    uni.stopPullDownRefresh?.()
  }
}

/** 统计行：与列表并行拉取，失败不阻断列表 */
const loadStats = async () => {
  try {
    const s: any = await api.memberStats()
    if (s && typeof s === 'object') {
      stats.value = {
        total: s.total ?? 0,
        newThisMonth: s.newThisMonth ?? 0,
        male: s.male ?? 0,
        female: s.female ?? 0,
      }
    }
  } catch {
    stats.value = null
  }
}

const refreshAll = () => {
  loadPage(true)
  loadStats()
}
const fetchList = refreshAll
onShow(refreshAll)
onReachBottom(() => {
  if (!finished.value && !loading.value) {
    page.value++
    loadPage(false)
  }
})
onPullDownRefresh(refreshAll)

/* ---------- 交互 ---------- */
const onSearch = () => fetchList()
const onClearKeyword = () => {
  keyword.value = ''
  fetchList()
}
const onChip = (c: (typeof chips)[number]) => {
  activeChip.value = c.label
  fetchList()
}
const onScope = (s: 'all' | 'mine') => {
  scope.value = s
  fetchList()
}
const onView = (m: Member) => uni.navigateTo({ url: `/pages/member/detail?id=${m.id}` })
const onMatch = (m: Member) => uni.navigateTo({ url: `/pages/match/index?memberId=${m.id}` })
const onCreate = () => uni.navigateTo({ url: '/pages/member/input' })
/** 搜索框麦克风：本页不做语音识别（避免给出无法兑现的能力），引导到 AI 智能录入页 */
const onMic = () => uni.navigateTo({ url: '/pages/member/input' })

/* ---------- 高级筛选弹层 ---------- */
const openFilter = () => {
  showFilter.value = true
}
const closeFilter = () => {
  showFilter.value = false
}
const applyFilter = () => {
  showFilter.value = false
  fetchList()
}
const resetFilter = () => {
  fCity.value = ''
  fEducation.value = ''
  fLevel.value = ''
  activeChip.value = '全部'
  keyword.value = ''
  scope.value = 'all'
  showFilter.value = false
  fetchList()
}

/* ---------- 展示工具 ---------- */
const tagList = (tags?: string) => (tags ? tags.split(/[,，、]/).filter(Boolean) : [])
const activeNow = (m: Member) => {
  const s = m.lastActive || ''
  return s.includes('刚刚') || s.includes('分钟')
}
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="loading" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 顶栏 -->
    <view class="glass-bar topbar">
      <text class="topbar-title">会员库</text>
      <view class="topbar-btn flex-center" hover-class="hover-opacity" @tap="openFilter">
        <app-icon name="sliders-ink" size="36rpx" />
        <view v-if="filterActive()" class="filter-dot" />
      </view>
    </view>

    <!-- 搜索栏 -->
    <view class="search-bar flex items-center">
      <app-icon name="search-ink" size="36rpx" />
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索姓名、职业、城市…"
        placeholder-class="ph"
        confirm-type="search"
        @confirm="onSearch"
      />
      <view v-if="keyword" class="flex-center" hover-class="hover-opacity" style="width: 56rpx; height: 56rpx" @tap="onClearKeyword">
        <app-icon name="close-ink" size="32rpx" />
      </view>
      <view v-else class="flex-center" hover-class="hover-opacity" style="width: 56rpx; height: 56rpx" @tap="onMic">
        <app-icon name="mic" size="36rpx" />
      </view>
    </view>

    <!-- 统计行 + 作用域切换 -->
    <view class="stats-row flex-between">
      <view class="stats-left">
        <text class="stats-text">共 <text class="stats-num">{{ stats ? stats.total : total.toLocaleString() }}</text> 位会员</text>
        <text v-if="stats" class="stats-sub">本月新增 {{ stats.newThisMonth }} · 男 {{ stats.male }} / 女 {{ stats.female }}</text>
      </view>
      <view class="scope-switch flex">
        <view class="scope-item flex-center" :class="{ 'scope-active': scope === 'all' }" @tap="onScope('all')">全部</view>
        <view class="scope-item flex-center" :class="{ 'scope-active': scope === 'mine' }" @tap="onScope('mine')">我负责</view>
      </view>
    </view>

    <!-- 筛选 chips（均为后端真实支持的维度） -->
    <scroll-view scroll-x enhanced :show-scrollbar="false" class="chips-scroll">
      <view class="flex chips-row">
        <view
          v-for="c in chips"
          :key="c.label"
          class="chip flex-center"
          :class="{ 'chip-active': activeChip === c.label }"
          hover-class="hover-opacity"
          @tap="onChip(c)"
        >{{ c.label }}</view>
        <view class="chip chip-more flex-center" hover-class="hover-opacity" @tap="openFilter">+ 更多</view>
      </view>
    </scroll-view>

    <!-- 会员卡片列表 -->
    <view class="member-list">
      <glass-card
        v-for="m in list"
        :key="m.id"
        strong
        padding="32rpx"
        radius="48rpx"
        shadow="normal"
        class="member-card"
        hover-class="hover-lift-card"
      >
        <view class="flex" style="gap: 28rpx">
          <view class="avatar-wrap">
            <view
              class="avatar flex-center"
              :style="m.photoThumb ? {} : { background: m.avatarBg, color: '#fff' }"
            >
              <image v-if="m.photoThumb" :src="m.photoThumb" class="avatar-img" mode="aspectFill" />
              <text v-else>{{ m.name[0] }}</text>
            </view>
            <view v-if="m.verified" class="badge-gold flex-center">
              <app-icon name="check-white" size="20rpx" />
            </view>
          </view>
          <view class="member-info">
            <view class="flex items-center" style="gap: 12rpx">
              <text class="member-name">{{ m.name }} · {{ m.age }}岁</text>
              <view class="gender-badge flex-center" :class="m.gender === '女' ? 'g-female' : 'g-male'">
                {{ m.gender === '女' ? '♀' : '♂' }}
              </view>
            </view>
            <text class="member-sub">{{ m.job || m.level }} · {{ m.city }} · {{ m.education }}</text>
            <view class="flex flex-wrap" style="gap: 12rpx; margin: 12rpx 0">
              <text v-for="t in tagList(m.tags).slice(0, 3)" :key="t" class="m-tag">{{ t }}</text>
            </view>
            <view class="flex items-center" style="gap: 8rpx">
              <view v-if="activeNow(m)" class="dot-active" />
              <app-icon v-else name="clock-warning" size="24rpx" />
              <text class="status-text" :class="activeNow(m) ? 'st-active' : 'st-idle'">
                {{ activeNow(m) ? '刚刚活跃' : m.lastActive || '待跟进' }}
              </text>
            </view>
          </view>
          <view class="btn-col">
            <view class="btn-view flex-center" hover-class="hover-opacity" @tap="onView(m)">查看</view>
            <view class="btn-match flex-center" hover-class="hover-opacity" @tap="onMatch(m)">牵线</view>
          </view>
        </view>
      </glass-card>
    </view>

    <!-- 空态：区分「库里还没有会员」与「筛选无结果」 -->
    <view v-if="firstLoaded && !loading && list.length === 0" class="empty flex-center">
      <view class="empty-icon flex-center">
        <app-icon name="users-primary" size="56rpx" />
      </view>
      <text class="empty-title">{{ filterActive() || keyword || activeChip !== '全部' ? '没有符合条件的会员' : '会员库还是空的' }}</text>
      <text class="empty-text">
        {{ filterActive() || keyword || activeChip !== '全部'
          ? '试试放宽筛选条件，或清空后重新查找'
          : '录入第一位会员，开始你的牵线服务' }}
      </text>
      <view v-if="filterActive() || keyword || activeChip !== '全部'" class="empty-btn flex-center" hover-class="hover-opacity" @tap="resetFilter">
        清空筛选
      </view>
      <view v-else class="empty-btn flex-center" hover-class="hover-opacity" @tap="onCreate">新建会员</view>
    </view>

    <!-- 加载更多提示 -->
    <view v-else-if="list.length > 0 && finished" class="list-end">
      <text class="list-end-text">已显示全部 {{ total }} 位会员</text>
    </view>

    <view style="height: 200rpx" />

    <!-- 悬浮新建按钮 -->
    <view class="fab flex-center" hover-class="hover-scale" @tap="onCreate">
      <app-icon name="plus-white" size="40rpx" />
    </view>

    <!-- 高级筛选弹层 -->
    <view v-if="showFilter" class="mask" @tap="closeFilter">
      <view class="sheet" @tap.stop>
        <view class="sheet-head flex-between items-center">
          <text class="sheet-title">高级筛选</text>
          <view class="flex-center" hover-class="hover-opacity" @tap="closeFilter">
            <app-icon name="close-ink" size="30rpx" />
          </view>
        </view>

        <text class="field-label">城市</text>
        <input v-model="fCity" class="field-input" placeholder="输入城市，如：上海" placeholder-class="field-ph" />

        <text class="field-label">学历</text>
        <view class="opt-row flex flex-wrap">
          <view
            v-for="o in EDU_OPTIONS"
            :key="o"
            class="opt flex-center"
            :class="{ 'opt-on': (o === '不限' ? !fEducation : fEducation === o) }"
            hover-class="hover-opacity"
            @tap="fEducation = o === '不限' ? '' : o"
          >{{ o }}</view>
        </view>

        <text class="field-label">会员等级</text>
        <view class="opt-row flex flex-wrap">
          <view
            v-for="o in LEVEL_OPTIONS"
            :key="o"
            class="opt flex-center"
            :class="{ 'opt-on': (o === '不限' ? !fLevel : fLevel === o) }"
            hover-class="hover-opacity"
            @tap="fLevel = o === '不限' ? '' : o"
          >{{ o }}</view>
        </view>

        <view class="sheet-actions flex">
          <view class="btn-reset flex-center" hover-class="hover-opacity" @tap="resetFilter">重置</view>
          <view class="btn-apply flex-center" hover-class="hover-opacity" @tap="applyFilter">查看结果</view>
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

/* ---------- 顶栏 ---------- */
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
.topbar-btn {
  position: relative;
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: var(--q-glass-bg);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}
.filter-dot {
  position: absolute;
  top: 14rpx;
  right: 14rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--q-gold-dark);
  border: 2rpx solid #fff;
}

/* ---------- 搜索栏 ---------- */
.search-bar {
  position: relative;
  z-index: 2;
  margin: 24rpx 40rpx 0;
  height: 96rpx;
  border-radius: 32rpx;
  padding: 0 32rpx;
  gap: 20rpx;
  background: var(--q-glass-bg);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}
.search-input {
  flex: 1;
  font-size: 28rpx;
  color: var(--q-ink-2);
}
.ph {
  color: var(--q-ink-4);
}

/* ---------- 统计行 ---------- */
.stats-row {
  position: relative;
  z-index: 2;
  margin: 28rpx 40rpx 0;
  align-items: center;
}
.stats-left {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.stats-text {
  font-size: 26rpx;
  color: var(--q-ink-3);
}
.stats-num {
  color: var(--q-primary);
  font-weight: 700;
}
.stats-sub {
  font-size: 21rpx;
  color: var(--q-ink-4);
}
.scope-switch {
  background: var(--q-glass-bg-light);
  border: 1px solid var(--q-glass-border);
  border-radius: 999rpx;
  padding: 6rpx;
  flex-shrink: 0;
}
.scope-item {
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.scope-active {
  background: var(--q-primary-soft-2);
  color: var(--q-primary);
  font-weight: 600;
}

/* ---------- 筛选 chips ---------- */
.chips-scroll {
  position: relative;
  z-index: 2;
  margin-top: 24rpx;
  white-space: nowrap;
}
.chips-row {
  padding: 0 40rpx;
  gap: 16rpx;
  width: max-content;
}
.chip {
  padding: 14rpx 32rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--q-ink-2);
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
}
.chip-active {
  background: var(--q-primary);
  color: #fff;
  border-color: var(--q-primary);
  font-weight: 600;
}
.chip-more {
  background: var(--q-gold-soft);
  color: var(--q-gold-dark);
  border-color: rgba(212, 175, 55, 0.25);
}

/* ---------- 会员卡片 ---------- */
.member-list {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}
.avatar {
  width: 116rpx;
  height: 116rpx;
  border-radius: 50%;
  overflow: hidden;
  font-size: 44rpx;
  font-weight: 700;
}
/* 会员照片缩略图（后端生成的 120px JPEG dataURL，列表专用） */
.avatar-img {
  width: 116rpx;
  height: 116rpx;
  border-radius: 50%;
}
.badge-gold {
  position: absolute;
  bottom: -2rpx;
  right: -2rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: var(--q-gold-gradient);
  border: 4rpx solid #fff;
}
.member-info {
  flex: 1;
  min-width: 0;
}
.member-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.gender-badge {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  font-size: 24rpx;
}
.g-female {
  background: var(--q-primary-soft-2);
  color: var(--q-primary);
}
.g-male {
  background: rgba(107, 124, 147, 0.12);
  color: #6b7c93;
}
.member-sub {
  display: block;
  font-size: 24rpx;
  color: var(--q-ink-3);
  margin-top: 6rpx;
}
.m-tag {
  padding: 6rpx 16rpx;
  border-radius: 12rpx;
  background: var(--q-primary-soft);
  font-size: 22rpx;
  color: var(--q-primary);
  white-space: nowrap;
}
.dot-active {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--q-success);
}
.status-text {
  font-size: 22rpx;
}
.st-active {
  color: var(--q-success);
}
.st-idle {
  color: var(--q-warning);
  font-weight: 500;
}
.btn-col {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  justify-content: center;
}
.btn-view {
  padding: 14rpx 28rpx;
  border-radius: 999rpx;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 24rpx;
  font-weight: 600;
}
.btn-match {
  padding: 14rpx 28rpx;
  border-radius: 999rpx;
  background: var(--q-gold-gradient);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
  text-shadow: 0 2rpx 6rpx rgba(120, 90, 10, 0.3);
}

/* ---------- 空态 / 列表结束 ---------- */
.empty,
.list-end {
  position: relative;
  z-index: 2;
  margin: 80rpx 40rpx 0;
  padding: 80rpx 48rpx;
  flex-direction: column;
  gap: 16rpx;
  border-radius: 48rpx;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}
.list-end {
  padding: 32rpx;
}
.list-end-text {
  font-size: 24rpx;
  color: var(--q-ink-4);
}
.empty-icon {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  background: var(--q-primary-soft);
  margin-bottom: 12rpx;
}
.empty-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.empty-text {
  font-size: 26rpx;
  color: var(--q-ink-3);
  text-align: center;
  line-height: 1.6;
}
.empty-btn {
  margin-top: 20rpx;
  padding: 20rpx 56rpx;
  border-radius: 999rpx;
  background: var(--q-gold-gradient);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  text-shadow: 0 2rpx 6rpx rgba(120, 90, 10, 0.3);
}

/* ---------- 悬浮新建 ---------- */
.fab {
  position: fixed;
  right: 40rpx;
  bottom: calc(160rpx + env(safe-area-inset-bottom));
  z-index: 6;
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--q-primary), #a52d58);
  box-shadow: 0 12rpx 32rpx rgba(139, 37, 74, 0.32);
}

/* ---------- 筛选弹层 ---------- */
.mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  background: rgba(28, 18, 22, 0.32);
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  padding: 36rpx 40rpx calc(36rpx + env(safe-area-inset-bottom));
  border-radius: 48rpx 48rpx 0 0;
  background: #fff;
  box-shadow: 0 -8rpx 40rpx rgba(28, 18, 22, 0.14);
}
.sheet-head {
  margin-bottom: 24rpx;
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.field-label {
  display: block;
  font-size: 25rpx;
  color: var(--q-ink-2);
  margin: 20rpx 0 12rpx;
  font-weight: 500;
}
.field-input {
  height: 84rpx;
  padding: 0 28rpx;
  border-radius: 24rpx;
  background: #f7f3f4;
  font-size: 27rpx;
  color: var(--q-ink-1);
}
.field-ph {
  color: var(--q-ink-4);
}
.opt-row {
  gap: 16rpx;
}
.opt {
  padding: 14rpx 32rpx;
  border-radius: 999rpx;
  background: #f7f3f4;
  font-size: 25rpx;
  color: var(--q-ink-2);
}
.opt-on {
  background: var(--q-primary);
  color: #fff;
  font-weight: 600;
}
.sheet-actions {
  margin-top: 40rpx;
  gap: 20rpx;
}
.btn-reset,
.btn-apply {
  flex: 1;
  height: 92rpx;
  border-radius: 24rpx;
  font-size: 29rpx;
  font-weight: 600;
}
.btn-reset {
  background: #f7f3f4;
  color: var(--q-ink-2);
}
.btn-apply {
  background: linear-gradient(135deg, var(--q-primary), #a52d58);
  color: #fff;
}

.hover-opacity {
  opacity: 0.6;
}
</style>
