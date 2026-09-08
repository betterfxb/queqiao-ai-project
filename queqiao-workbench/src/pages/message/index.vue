<script setup lang="ts">
/**
 * 消息中心 — Tab 主页（严格按设计稿：顶栏/快捷双卡/分段器/会话列表）
 * 数据：GET /api/conversations
 */
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { api, type Conversation } from '@/api'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

const segs = [
  { key: '', label: '全部' },
  { key: 'member', label: '会员消息' },
  { key: 'system', label: '系统通知' },
]
const activeSeg = ref('')
const list = ref<Conversation[]>([])
const loading = ref(false)
/** 未读数：来自 GET /api/conversations/unread，替代原先写死的「3条未读」 */
const unread = ref<{ total: number; member: number; system: number; conversations: number }>({
  total: 0,
  member: 0,
  system: 0,
  conversations: 0,
})

/**
 * 会话列表：后端 /api/conversations 直接返回「裸数组」（见 api/index.ts 契约说明），
 * 这里对 { list } 包装结构做兼容，避免两种形态下取到 undefined。
 */
const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await api.conversations(activeSeg.value || undefined)
    const arr = Array.isArray(res) ? res : res?.list ?? []
    list.value = arr as Conversation[]
  } catch {
    list.value = []
    uni.showToast({ title: '后端未连接', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh?.()
  }
}

const fetchUnread = async () => {
  try {
    const r: any = await api.conversationUnread()
    if (r && typeof r === 'object') unread.value = { total: r.total ?? 0, member: r.member ?? 0, system: r.system ?? 0, conversations: r.conversations ?? 0 }
  } catch {
    /* 未读数获取失败不阻断列表展示 */
  }
}

const refreshAll = async () => {
  await Promise.all([fetchList(), fetchUnread()])
}
onShow(refreshAll)
onPullDownRefresh(refreshAll)

const onSeg = (key: string) => {
  activeSeg.value = key
  fetchList()
}

/**
 * 头像图标：后端 kind 仅有 member / ai / system / group 四值（无 icon 字段），
 * 会员会话使用 avatarBg + 姓名首字，其余按 kind 取对应图标。
 */
const iconOf = (c: Conversation) =>
  c.kind === 'ai' ? 'bot-white' : c.kind === 'system' ? 'megaphone-gold' : 'users-primary'
/** 会员会话用渐变头像 + 首字，非会员用图标头像 */
const isMemberKind = (c: Conversation) => c.kind === 'member'
const initialOf = (c: Conversation) => (c.name || '会').slice(0, 1)

const onOpen = (c: Conversation) => {
  c.unread = 0
  uni.navigateTo({ url: `/pages/message/chat?id=${c.id}&name=${encodeURIComponent(c.name || '会话')}` })
}

/**
 * 快捷卡：AI 红娘助手
 * 跳转到真实可用的 AI 助手页（约会教练 / 回复建议，均落地后端接口），
 * 不再用 toast 软引导。
 */
const onAiCard = () => {
  uni.navigateTo({ url: '/pages/ai-coach/index' })
}
const onSysCard = () => {
  activeSeg.value = 'system'
  fetchList()
}
/** 新建会话：从会员库选择（会话必须挂在一个会员上，后端按 memberId 创建） */
const onCompose = () => uni.switchTab({ url: '/pages/members/index' })
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="loading" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 顶栏（大字号） -->
    <view class="glass-bar topbar">
      <text class="topbar-title">消息</text>
      <view class="circle-btn flex-center" hover-class="hover-opacity" @tap="onCompose">
        <app-icon name="pen-ink" size="36rpx" />
      </view>
    </view>

    <!-- 快捷双卡（Bento） -->
    <view class="bento quick-bento">
      <glass-card
        padding="28rpx"
        radius="var(--q-bento-radius)"
        shadow="soft"
        class="qc"
        hover-class="hover-lift-card"
        @tap="onAiCard"
      >
        <view class="flex items-center" style="gap: 20rpx">
          <view class="qc-icon qc-ai flex-center anim-breathe">
            <app-icon name="bot-white" size="36rpx" />
          </view>
          <view>
            <text class="qc-title">AI 红娘助手</text>
            <text class="qc-sub">智能回复建议</text>
          </view>
        </view>
      </glass-card>
      <glass-card
        padding="28rpx"
        radius="var(--q-bento-radius)"
        shadow="soft"
        class="qc"
        hover-class="hover-lift-card"
        @tap="onSysCard"
      >
        <view class="flex items-center" style="gap: 20rpx">
          <view class="qc-icon qc-sys flex-center">
            <app-icon name="bell-gold" size="36rpx" />
          </view>
          <view>
            <text class="qc-title">系统通知</text>
            <text class="qc-sub" :class="{ 'qc-unread': unread.system > 0 }">
              {{ unread.system > 0 ? `${unread.system}条未读` : '暂无未读' }}
            </text>
          </view>
        </view>
      </glass-card>
    </view>

    <!-- 分段器 -->
    <view class="seg">
      <view
        v-for="s in segs"
        :key="s.key"
        class="seg-item flex-center"
        :class="{ 'seg-active': activeSeg === s.key }"
        @tap="onSeg(s.key)"
      >{{ s.label }}</view>
    </view>

    <!-- 会话列表 -->
    <view class="chat-list">
      <view
        v-for="c in list"
        :key="c.id"
        class="chat-item flex items-center"
        hover-class="chat-hover"
        @tap="onOpen(c)"
      >
        <view class="avatar-wrap">
          <view
            v-if="isMemberKind(c)"
            class="chat-avatar flex-center"
            :style="{ background: c.avatarBg || 'linear-gradient(135deg,#E8C7D4,#8B254A)', color: '#fff' }"
          >{{ initialOf(c) }}</view>
          <view
            v-else
            class="chat-avatar flex-center"
            :class="c.kind === 'ai' ? 'ca-ai' : c.kind === 'system' ? 'ca-sys' : 'ca-group'"
          >
            <app-icon :name="iconOf(c)" size="40rpx" />
          </view>
          <view v-if="c.online" class="online-dot" />
          <view v-if="c.unread > 0" class="unread-badge flex-center">{{ c.unread }}</view>
        </view>
        <view class="chat-body">
          <view class="flex-between items-center">
            <text class="chat-name">{{ c.name }}</text>
            <text class="chat-time">{{ c.timeAgo }}</text>
          </view>
          <text class="chat-snippet">{{ c.preview || '暂无消息' }}</text>
        </view>
      </view>
    </view>

    <!-- 空态：库里还没有任何会话（数据已清空/尚未开始沟通） -->
    <view v-if="!loading && list.length === 0" class="empty flex-center">
      <view class="empty-icon flex-center">
        <app-icon name="message-primary" size="56rpx" />
      </view>
      <text class="empty-title">暂无会话</text>
      <text class="empty-text">
        {{ activeSeg === 'system' ? '还没有系统通知' : '去会员库选择会员，发起第一次沟通' }}
      </text>
      <view class="empty-btn flex-center" hover-class="hover-opacity" @tap="onCompose">去会员库</view>
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

/* ---------- 快捷双卡（Bento） ---------- */
.quick-bento {
  position: relative;
  z-index: 2;
  margin: 24rpx 40rpx 0;
}
.qc {
  flex: 1;
  min-height: 128rpx;
  display: flex;
  align-items: center;
}
.qc-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  flex-shrink: 0;
}
.qc-ai {
  background: linear-gradient(135deg, var(--q-primary), var(--q-gold));
}
.qc-sys {
  background: var(--q-gold-soft);
}
.qc-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.qc-sub {
  display: block;
  font-size: 22rpx;
  color: var(--q-ink-3);
  margin-top: 4rpx;
}
.qc-unread {
  color: var(--q-error);
}

/* ---------- 分段器 ---------- */
.seg {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
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
  padding: 16rpx 0;
  border-radius: 20rpx;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--q-ink-3);
  transition: all 0.2s ease;
}
.seg-active {
  background: var(--q-primary);
  color: #fff;
  font-weight: 600;
}

/* ---------- 会话列表 ---------- */
.chat-list {
  position: relative;
  z-index: 2;
  margin-top: 16rpx;
}
.chat-item {
  padding: 28rpx 40rpx;
  gap: 24rpx;
  border-bottom: 1px solid rgba(139, 37, 74, 0.06);
  min-height: 152rpx;
  box-sizing: border-box;
}
.chat-hover {
  background: var(--q-primary-soft);
}
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}
.chat-avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  font-size: 34rpx;
  font-weight: 600;
}
.ca-ai {
  background: linear-gradient(135deg, var(--q-primary), var(--q-gold));
}
.ca-sys {
  background: var(--q-gold-soft);
}
.ca-group {
  background: var(--q-primary-soft);
}
.unread-badge {
  position: absolute;
  top: -4rpx;
  right: -4rpx;
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 8rpx;
  border-radius: 18rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  border: 4rpx solid var(--q-bg-base);
  box-sizing: border-box;
}
.chat-body {
  flex: 1;
  min-width: 0;
}
.chat-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-ink-1);
  max-width: 70%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-time {
  font-size: 22rpx;
  color: var(--q-ink-3);
  flex-shrink: 0;
}
.chat-snippet {
  display: block;
  font-size: 26rpx;
  color: var(--q-ink-3);
  margin-top: 6rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 在线小绿点 ---------- */
.online-dot {
  position: absolute;
  bottom: 2rpx;
  right: 2rpx;
  width: 22rpx;
  height: 22rpx;
  border-radius: 50%;
  background: var(--q-success);
  border: 4rpx solid var(--q-bg-base);
  box-sizing: border-box;
}

/* ---------- 空态 ---------- */
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
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
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

.hover-opacity {
  opacity: 0.6;
}
</style>
