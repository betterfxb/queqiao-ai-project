<script setup lang="ts">
/**
 * 牵线跟进看板
 * 模块：胶囊分段器（牵线中/待反馈/已约见/已脱单）/ 卡片瀑布流
 * 卡片：双方头像并排 + 姓名 + 匹配度环形进度（香槟金）+ 状态标签 + 四个微按钮
 */
import { computed, ref, nextTick } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api } from '@/api'
import { toDisplay, canAction, type PairAction } from '@/utils/pairStatus'

/* ---------- 状态栏 ---------- */
const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

/* ---------- 分段器（对齐后端真实生命周期） ---------- */
const tabs = ['待确认', '牵线中', '已约见', '已脱单', '已终止'] as const
type TabKey = (typeof tabs)[number]
const activeTab = ref<TabKey>('待确认')

/* ---------- 看板数据（API） ---------- */
interface Brief {
  id: string
  name: string
  avatarBg: string
  avatarColor: string
}
interface PairCard {
  id: string
  male: Brief
  female: Brief
  matchScore: number
  status: string
  statusLabel: string
}

// 状态枚举归一：后端真实状态含 待确认/初步推荐/牵线中/已联系/跟进中/待反馈/已约见/已脱单/已终止
const STATUS_NORMALIZE: Record<string, string> = {
  待确认: '待确认',
  初步推荐: '牵线中',
  牵线中: '牵线中',
  已联系: '牵线中',
  跟进中: '牵线中',
  待反馈: '牵线中',
  已约见: '已约见',
  已脱单: '已脱单',
  已成功: '已脱单',
  已终止: '已终止',
  matching: '牵线中', pending: '待确认', met: '已约见', success: '已脱单', failed: '已终止', done: '已脱单',
}
const normalizeStatus = (s?: string) => STATUS_NORMALIZE[s || ''] || '待确认'

const cards = ref<PairCard[]>([])
const loading = ref(false)

// 从会员详情「牵线记录」带入的指定牵线：自动定位并高亮
const highlightId = ref('')
onLoad((q: any) => {
  if (q?.id) highlightId.value = String(q.id)
})

const fetchPairs = async () => {
  loading.value = true
  try {
    const res = await api.pairs()
    cards.value = (res.list || []).map((p) => {
      const status = normalizeStatus(p.status)
      return {
        id: String(p.id),
        male: { id: p.maleId || '', name: p.maleName || '男嘉宾', avatarBg: '', avatarColor: '' },
        female: { id: p.femaleId || '', name: p.femaleName || '女嘉宾', avatarBg: '', avatarColor: '' },
        matchScore: typeof p.score === 'number' ? p.score : 0,
        status,
        statusLabel: p.statusLabel || status,
      } as PairCard
    })
    // 定位来自会员详情的指定牵线：切到其所在状态分段并滚动高亮
    if (highlightId.value) {
      const hit = (res.list || []).find((p: any) => String(p.id) === highlightId.value)
      if (hit) {
        activeTab.value = (normalizeStatus(hit.status) || '待确认') as TabKey
        nextTick(() => {
          uni
            .createSelectorQuery()
            .select(`#pc-${highlightId.value}`)
            .boundingClientRect((r: any) => {
              if (r) uni.pageScrollTo({ scrollTop: Math.max(0, r.top - 24), duration: 300 })
            })
            .exec()
        })
      }
    }
  } catch {
    uni.showToast({ title: '后端未连接', icon: 'none' })
  } finally {
    loading.value = false
  }
}
onShow(fetchPairs)

const filteredCards = computed(() => cards.value.filter((c) => c.status === activeTab.value))

/* ---------- 卡片操作 ---------- */
const LABEL_MAP: Record<string, string> = { call: '跟进', schedule: '约见', done: '完成', fail: '失败' }
// 按钮动作 -> 目标状态（与后端 PUT /api/pairs/{id} 契约对齐；done 发后端枚举「已成功」触发分佣/积分）
const ACTION_STATUS: Record<string, string> = { call: '牵线中', schedule: '已约见', done: '已成功', fail: '已终止' }

/** 当前卡片状态是否允许该动作（对齐后端状态机，前端禁用非法按钮） */
const actionAllowed = (type: PairAction, c: PairCard) => canAction(type, c.status)

const onAction = async (type: PairAction, c: PairCard) => {
  if (!actionAllowed(type, c)) {
    uni.showToast({ title: '当前状态不支持该操作', icon: 'none' })
    return
  }
  const next = ACTION_STATUS[type]
  const label = LABEL_MAP[type]
  try {
    await api.updatePairStatus(String(c.id), next)
    // 更新本地状态（转回显示态），使看板分段器实时流转
    c.status = toDisplay(next)
    c.statusLabel = toDisplay(next)
    uni.showToast({ title: `${label}成功`, icon: 'success' })
  } catch (e: any) {
    // 后端状态机可能拒绝非法流转（400），给出可读提示而非笼统失败
    const msg = e?.message || ''
    uni.showToast({ title: msg.includes('状态') ? '状态流转不被允许' : `${label}失败`, icon: 'none' })
  }
}

/** 点击卡片进入牵线详情（与详情页历史图标形成双向闭环） */
const goPair = (c: PairCard) => {
  uni.navigateTo({ url: `/pages/pair/detail?id=${c.id}` })
}
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="loading" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 胶囊分段器 -->
    <view class="seg-wrap">
      <view class="seg">
        <view
          v-for="t in tabs"
          :key="t"
          class="seg-item flex-center"
          :class="{ 'seg-active': activeTab === t }"
          @tap="activeTab = t"
        >{{ t }}</view>
      </view>
    </view>

    <!-- 卡片瀑布流 -->
    <view class="card-list">
      <view
        v-for="c in filteredCards"
        :key="c.id"
        :id="'pc-' + c.id"
        class="pair-card-wrap"
        :class="{ hl: c.id === highlightId }"
      >
      <glass-card
        strong
        padding="32rpx"
        shadow="normal"
        class="pair-card"
        hover-class="hover-opacity"
        @tap="goPair(c)"
      >
        <view class="flex items-center">
          <!-- 左侧：双方头像并排 -->
          <view class="duo-avatar">
            <view class="duo duo-male flex-center" :style="{ background: c.male.avatarBg }">
              {{ c.male.name[0] }}
            </view>
            <view class="duo duo-female flex-center" :style="{ background: c.female.avatarBg }">
              {{ c.female.name[0] }}
            </view>
          </view>

          <!-- 中部：姓名 + 匹配度环 -->
          <view class="pair-info">
            <text class="pair-names">{{ c.male.name }} × {{ c.female.name }}</text>
            <view class="flex items-center" style="gap: 16rpx; margin-top: 12rpx">
              <progress-ring :percent="c.matchScore" size="84rpx" stroke="8rpx" />
              <view>
                <text class="match-caption">匹配度</text>
                <text class="match-hint">{{ c.matchScore >= 90 ? '天作之合' : c.matchScore >= 80 ? '十分契合' : '值得推进' }}</text>
              </view>
            </view>
          </view>

          <!-- 右侧：状态标签 -->
          <view class="status-tag">{{ c.statusLabel }}</view>
        </view>

        <!-- 底部操作行（.stop 阻止冒泡，避免触发卡片跳转） -->
        <view class="op-row">
          <view class="op-btn flex-center" :class="{ 'op-disabled': !actionAllowed('call', c) }" hover-class="hover-opacity" @tap.stop="onAction('call', c)">📞 跟进</view>
          <view class="op-btn flex-center" :class="{ 'op-disabled': !actionAllowed('schedule', c) }" hover-class="hover-opacity" @tap.stop="onAction('schedule', c)">📅 约见</view>
          <view class="op-btn op-success flex-center" :class="{ 'op-disabled': !actionAllowed('done', c) }" hover-class="hover-opacity" @tap.stop="onAction('done', c)">✅ 完成</view>
          <view class="op-btn op-danger flex-center" :class="{ 'op-disabled': !actionAllowed('fail', c) }" hover-class="hover-opacity" @tap.stop="onAction('fail', c)">❌ 失败</view>
        </view>
      </glass-card>
      </view>

      <!-- 空态 -->
      <view v-if="filteredCards.length === 0" class="empty flex-center">
        <text class="empty-text">当前状态下暂无牵线记录</text>
      </view>
    </view>

    <view style="height: 40rpx" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: transparent;
}

/* ---------- 胶囊分段器 ---------- */
.seg-wrap {
  position: relative;
  z-index: 2;
  padding: 24rpx 40rpx 8rpx;
}
.seg {
  display: flex;
  background: rgba(139, 37, 74, 0.06);
  border-radius: 999rpx;
  padding: 8rpx;
  gap: 8rpx;
}
.seg-item {
  flex: 1;
  height: 72rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--q-ink-3);
  transition: all 0.2s ease;
}
.seg-active {
  background: var(--q-primary);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 8rpx 20rpx rgba(139, 37, 74, 0.28);
}

/* ---------- 卡片列表 ---------- */
.card-list {
  position: relative;
  z-index: 2;
  padding: 24rpx 40rpx 0;
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}
.pair-card {
  border-radius: 56rpx;
}
.pair-card-wrap {
  border-radius: 56rpx;
  transition: box-shadow 0.3s ease, transform 0.3s ease;
}
.pair-card-wrap.hl {
  box-shadow: 0 0 0 4rpx var(--q-gold), 0 18rpx 44rpx rgba(212, 175, 55, 0.35);
  transform: translateY(-4rpx);
}

/* ---------- 双方头像 ---------- */
.duo-avatar {
  position: relative;
  width: 112rpx;
  height: 72rpx;
  flex-shrink: 0;
}
.duo {
  position: absolute;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  font-size: 28rpx;
  font-weight: 700;
  color: #fff;
  border: 4rpx solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 6rpx 16rpx rgba(120, 70, 80, 0.15);
}
.duo-male {
  left: 0;
  z-index: 1;
}
.duo-female {
  right: 0;
}

/* ---------- 中部信息 ---------- */
.pair-info {
  flex: 1;
  margin-left: 24rpx;
  min-width: 0;
}
.pair-names {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--q-ink-1);
  letter-spacing: -0.01em;
}
.match-caption {
  display: block;
  font-size: 22rpx;
  color: var(--q-ink-3);
}
.match-hint {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--q-gold-dark);
  margin-top: 4rpx;
}

/* ---------- 状态标签 ---------- */
.status-tag {
  flex-shrink: 0;
  align-self: flex-start;
  max-width: 220rpx;
  padding: 10rpx 20rpx;
  border-radius: 16rpx;
  background: var(--q-gold-soft);
  border: 2rpx solid rgba(212, 175, 55, 0.28);
  font-size: 22rpx;
  font-weight: 600;
  color: var(--q-gold-dark);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- 底部操作行 ---------- */
.op-row {
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1px solid rgba(139, 37, 74, 0.06);
}
.op-btn {
  flex: 1;
  height: 68rpx;
  border-radius: 20rpx;
  background: rgba(139, 37, 74, 0.06);
  font-size: 24rpx;
  font-weight: 600;
  color: var(--q-ink-2);
}
.op-success {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.op-danger {
  background: var(--q-error-soft);
  color: var(--q-error);
}
.op-disabled {
  opacity: 0.35;
  pointer-events: none;
}

/* ---------- 空态 ---------- */
.empty {
  position: relative;
  z-index: 2;
  padding: 120rpx 0;
}
.empty-text {
  font-size: 26rpx;
  color: var(--q-ink-3);
}

/* ---------- 触摸反馈 ---------- */
.hover-opacity {
  opacity: 0.6;
}
</style>
