<script setup lang="ts">
/**
 * AI 红娘助手 — 真实可用的 AI 增效工作台
 *  · 约会教练：选一条「进行中」的牵线 → GET /api/ai/coach/next-step/{pairId}（AI 不可用时本地降级）
 *  · 回复建议：选一段会话 → POST /api/conversations/{id}/reply-suggest
 * 两个能力都落地到后端真实接口，不再只是引导 toast。
 */
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { api, type Pair, type Conversation } from '@/api'
import { toDisplay, isTerminal } from '@/utils/pairStatus'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

type Mode = 'coach' | 'reply'
const mode = ref<Mode>('coach')

/* ---------- 约会教练 ---------- */
interface CoachPair {
  id: string
  name: string
  status: string
  score: number
}
const pairs = ref<CoachPair[]>([])
const coachLoading = ref(false)
const selectedPairId = ref('')
const coachAdvice = ref<Record<string, any> | null>(null)
const coachLoadingAdvice = ref(false)

const fetchPairs = async () => {
  coachLoading.value = true
  try {
    const res: any = await api.pairs()
    const list: Pair[] = res?.list || []
    pairs.value = list
      .map((p) => ({
        id: String(p.id),
        name: `${p.femaleName || '女嘉宾'} × ${p.maleName || '男嘉宾'}`,
        status: toDisplay(p.status || '待确认'),
        score: typeof p.score === 'number' ? p.score : 0,
      }))
      .filter((p) => !isTerminal(p.status))
  } catch {
    pairs.value = []
    uni.showToast({ title: '后端未连接', icon: 'none' })
  } finally {
    coachLoading.value = false
  }
}

const onSelectPair = async (id: string) => {
  if (selectedPairId.value === id) return
  selectedPairId.value = id
  coachAdvice.value = null
  coachLoadingAdvice.value = true
  try {
    const r: any = await api.aiCoachNextStep(id)
    coachAdvice.value = r || null
  } catch (e: any) {
    uni.showToast({ title: e?.message || '获取建议失败', icon: 'none' })
  } finally {
    coachLoadingAdvice.value = false
  }
}

/* ---------- 回复建议 ---------- */
const convs = ref<Conversation[]>([])
const replyLoading = ref(false)
const selectedConvId = ref('')
const suggestions = ref<string[]>([])
const replyLoadingSug = ref(false)

const fetchConvs = async () => {
  replyLoading.value = true
  try {
    const res: any = await api.conversations()
    const arr = Array.isArray(res) ? res : res?.list ?? []
    convs.value = (arr as Conversation[]).filter((c) => c.kind === 'member')
  } catch {
    convs.value = []
  } finally {
    replyLoading.value = false
  }
}

const onSelectConv = async (c: Conversation) => {
  selectedConvId.value = c.id
  suggestions.value = []
  replyLoadingSug.value = true
  try {
    const r: any = await api.replySuggest(c.id)
    suggestions.value = r?.suggestions ?? []
    if (suggestions.value.length === 0) {
      uni.showToast({ title: '先沟通几轮再生成建议', icon: 'none' })
    }
  } catch (e: any) {
    uni.showToast({ title: e?.message || '生成失败', icon: 'none' })
  } finally {
    replyLoadingSug.value = false
  }
}

const goChat = (c: Conversation) => {
  uni.navigateTo({ url: `/pages/message/chat?id=${c.id}&name=${encodeURIComponent(c.name || '会话')}` })
}

/* ---------- 切换分栏时按需加载 ---------- */
const onMode = (m: Mode) => {
  if (m === mode.value) return
  mode.value = m
  if (m === 'coach' && pairs.value.length === 0) fetchPairs()
  if (m === 'reply' && convs.value.length === 0) fetchConvs()
}

const onLoadInit = (q: any) => {
  // 允许从牵线详情带 ?pairId= 直接进入约会教练并选中
  if (q?.pairId) {
    mode.value = 'coach'
    fetchPairs().then(() => onSelectPair(String(q.pairId)))
  } else {
    fetchPairs()
  }
}
onLoad(onLoadInit)

const goBack = () => uni.navigateBack()

/* ---------- 建议渲染辅助 ---------- */
const adviceList = (key: string): string[] =>
  Array.isArray(coachAdvice.value?.[key]) ? (coachAdvice.value![key] as string[]) : []
const isLocal = computed(() => coachAdvice.value?.source === 'local-fallback')
</script>

<template>
  <view class="page">
    <aurora-bg />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 顶栏 -->
    <view class="glass-bar topbar">
      <view class="circle-btn flex-center" hover-class="hover-opacity" @tap="goBack">
        <app-icon name="back-ink" size="44rpx" />
      </view>
      <text class="topbar-title">AI 红娘助手</text>
      <view class="circle-btn flex-center" @tap="mode === 'coach' ? fetchPairs() : fetchConvs()">
        <app-icon name="refresh-ink" size="34rpx" />
      </view>
    </view>

    <!-- 分段器 -->
    <view class="seg">
      <view class="seg-item flex-center" :class="{ 'seg-active': mode === 'coach' }" @tap="onMode('coach')">约会教练</view>
      <view class="seg-item flex-center" :class="{ 'seg-active': mode === 'reply' }" @tap="onMode('reply')">回复建议</view>
    </view>

    <!-- ============ 约会教练 ============ -->
    <template v-if="mode === 'coach'">
      <view v-if="coachLoading" class="state-tip">加载牵线中…</view>
      <view v-else-if="pairs.length === 0" class="empty flex-center">
        <view class="empty-icon flex-center"><app-icon name="heart-white" size="48rpx" /></view>
        <text class="empty-title">暂无进行中的牵线</text>
        <text class="empty-text">去「牵线」或「跟进看板」发起并推进一条牵线，<br/>再来获取 AI 约会教练建议</text>
      </view>

      <template v-else>
        <view class="picker-label">选择一条牵线，获取下一步推进建议</view>
        <view class="pair-list">
          <view
            v-for="p in pairs"
            :key="p.id"
            class="pair-item flex-between"
            :class="{ 'sel': selectedPairId === p.id }"
            hover-class="hover-opacity"
            @tap="onSelectPair(p.id)"
          >
            <view class="flex items-center" style="gap: 16rpx; min-width: 0">
              <view class="pair-dot" />
              <view style="min-width: 0">
                <text class="pair-name">{{ p.name }}</text>
                <text class="pair-sub">{{ p.status }} · 匹配度 {{ p.score }}</text>
              </view>
            </view>
            <app-icon :name="selectedPairId === p.id ? 'check-circle-success' : 'chevron-right-ink'" size="32rpx" />
          </view>
        </view>

        <!-- 建议面板 -->
        <view v-if="selectedPairId" class="advice glass-strong">
          <view class="advice-head flex-between">
            <view class="flex items-center" style="gap: 12rpx">
              <app-icon name="sparkles-gold" size="30rpx" />
              <text class="advice-title">约会教练建议</text>
            </view>
            <text class="src-badge" :class="{ 'src-local': isLocal }">{{ isLocal ? '本地降级' : 'AI' }}</text>
          </view>

          <view v-if="coachLoadingAdvice" class="state-tip">AI 分析中…</view>
          <view v-else-if="coachAdvice" class="advice-body">
            <view v-if="coachAdvice.stageLabel" class="stage-pill">{{ coachAdvice.stageLabel }}</view>

            <view v-if="coachAdvice.nextStep" class="blk">
              <text class="blk-title">下一步</text>
              <text class="blk-text">{{ coachAdvice.nextStep }}</text>
              <text v-if="coachAdvice.actionWindow" class="win-badge">建议窗口：{{ coachAdvice.actionWindow }}</text>
            </view>

            <view v-if="adviceList('topics').length" class="blk">
              <text class="blk-title">聊资话题</text>
              <view v-for="t in adviceList('topics')" :key="t" class="chip">{{ t }}</view>
            </view>

            <view v-if="adviceList('openers').length" class="blk">
              <text class="blk-title">破冰开场白</text>
              <view v-for="t in adviceList('openers')" :key="t" class="line">{{ t }}</view>
            </view>

            <view v-if="adviceList('deepenQuestions').length" class="blk">
              <text class="blk-title">升温追问</text>
              <view v-for="t in adviceList('deepenQuestions')" :key="t" class="line">{{ t }}</view>
            </view>

            <view v-if="adviceList('tips').length" class="blk">
              <text class="blk-title">红娘贴士</text>
              <view v-for="t in adviceList('tips')" :key="t" class="line">{{ t }}</view>
            </view>

            <view v-if="adviceList('outfit').length" class="blk">
              <text class="blk-title">穿搭建议</text>
              <view v-for="t in adviceList('outfit')" :key="t" class="line">{{ t }}</view>
            </view>

            <view v-if="adviceList('warnings').length" class="warn-box">
              <text v-for="t in adviceList('warnings')" :key="t" class="warn-text">{{ t }}</text>
            </view>
          </view>
          <view v-else class="state-tip">未获取到建议</view>
        </view>
      </template>
    </template>

    <!-- ============ 回复建议 ============ -->
    <template v-else>
      <view v-if="replyLoading" class="state-tip">加载会话中…</view>
      <view v-else-if="convs.length === 0" class="empty flex-center">
        <view class="empty-icon flex-center"><app-icon name="message-primary" size="48rpx" /></view>
        <text class="empty-title">暂无会员会话</text>
        <text class="empty-text">去会员库选择会员发起沟通，<br/>再回来获取 AI 回复建议</text>
      </view>

      <template v-else>
        <view class="picker-label">选择一段会话，生成智能回复话术</view>
        <view class="pair-list">
          <view
            v-for="c in convs"
            :key="c.id"
            class="pair-item flex-between"
            :class="{ 'sel': selectedConvId === c.id }"
            hover-class="hover-opacity"
            @tap="onSelectConv(c)"
          >
            <view style="min-width: 0">
              <text class="pair-name">{{ c.name }}</text>
              <text class="pair-sub">{{ c.preview || '暂无消息' }}</text>
            </view>
            <app-icon :name="selectedConvId === c.id ? 'check-circle-success' : 'chevron-right-ink'" size="32rpx" />
          </view>
        </view>

        <view v-if="selectedConvId" class="advice glass-strong">
          <view class="advice-head flex-between">
            <view class="flex items-center" style="gap: 12rpx">
              <app-icon name="sparkles-gold" size="30rpx" />
              <text class="advice-title">回复建议</text>
            </view>
            <view class="flex items-center" style="gap: 12rpx">
              <view class="go-chat flex-center" hover-class="hover-opacity" @tap="goChat(convs.find(x => x.id === selectedConvId)!)">去沟通</view>
            </view>
          </view>

          <view v-if="replyLoadingSug" class="state-tip">AI 生成中…</view>
          <view v-else-if="suggestions.length" class="advice-body">
            <view
              v-for="(s, i) in suggestions"
              :key="i"
              class="sug-item"
              hover-class="hover-opacity"
              @tap="goChat(convs.find(x => x.id === selectedConvId)!)"
            >{{ s }}</view>
          </view>
          <view v-else class="state-tip">先沟通几轮，AI 才能给出贴切建议</view>
        </view>
      </template>
    </template>

    <view style="height: 60rpx" />
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
  font-weight: 700;
  color: var(--q-ink-1);
}
.circle-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}

/* ---------- 分段器 ---------- */
.seg {
  position: relative;
  z-index: 2;
  margin: 24rpx 40rpx 0;
  display: flex;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  border-radius: 28rpx;
  padding: 8rpx;
}
.seg-item {
  flex: 1;
  padding: 18rpx 0;
  border-radius: 20rpx;
  font-size: 27rpx;
  font-weight: 500;
  color: var(--q-ink-3);
  transition: all 0.2s ease;
}
.seg-active {
  background: var(--q-primary);
  color: #fff;
  font-weight: 600;
}

/* ---------- 提示 / 空态 ---------- */
.state-tip {
  position: relative;
  z-index: 2;
  text-align: center;
  font-size: 26rpx;
  color: var(--q-ink-3);
  padding: 40rpx;
}
.empty {
  position: relative;
  z-index: 2;
  margin: 80rpx 40rpx 0;
  padding: 80rpx 48rpx;
  flex-direction: column;
  gap: 16rpx;
  border-radius: var(--q-card-radius, 48rpx);
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
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

/* ---------- 选择器 ---------- */
.picker-label {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.pair-list {
  position: relative;
  z-index: 2;
  margin: 16rpx 40rpx 0;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.pair-item {
  padding: 24rpx 28rpx;
  border-radius: 28rpx;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
}
.pair-item.sel {
  border-color: var(--q-primary);
  background: var(--q-primary-soft);
}
.pair-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--q-gold);
  flex-shrink: 0;
}
.pair-name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--q-ink-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pair-sub {
  display: block;
  font-size: 22rpx;
  color: var(--q-ink-3);
  margin-top: 4rpx;
}

/* ---------- 建议面板 ---------- */
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
.advice-head {
  margin-bottom: 16rpx;
}
.advice-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.src-badge {
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 600;
  background: var(--q-gold-soft);
  color: var(--q-gold-dark);
}
.src-local {
  background: var(--q-warning-soft);
  color: var(--q-warning);
}
.go-chat {
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 22rpx;
  font-weight: 600;
}
.advice-body {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.stage-pill {
  align-self: flex-start;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  background: var(--q-gold-soft);
  color: var(--q-gold-dark);
  font-size: 24rpx;
  font-weight: 700;
}
.blk {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.blk-title {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--q-primary);
}
.blk-text {
  font-size: 27rpx;
  color: var(--q-ink-1);
  line-height: 1.6;
}
.win-badge {
  align-self: flex-start;
  margin-top: 4rpx;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(139, 37, 74, 0.06);
  color: var(--q-ink-2);
  font-size: 22rpx;
}
.chip {
  align-self: flex-start;
  padding: 10rpx 22rpx;
  border-radius: 16rpx;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 24rpx;
}
.line {
  font-size: 26rpx;
  color: var(--q-ink-2);
  line-height: 1.6;
  padding-left: 18rpx;
  border-left: 4rpx solid var(--q-gold);
}
.sug-item {
  padding: 20rpx 24rpx;
  border-radius: 18rpx;
  background: var(--q-primary-soft);
  font-size: 25rpx;
  color: var(--q-ink-1);
  line-height: 1.5;
}
.warn-box {
  padding: 18rpx 22rpx;
  border-radius: 18rpx;
  background: var(--q-warning-soft);
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.warn-text {
  font-size: 22rpx;
  color: var(--q-warning);
  line-height: 1.5;
}

.hover-opacity {
  opacity: 0.6;
}
</style>
