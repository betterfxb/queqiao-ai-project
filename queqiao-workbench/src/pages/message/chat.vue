<script setup lang="ts">
/**
 * 会话详情（沟通页）— 红娘与会员一对一沟通
 *
 * 数据契约（已实测核对后端返回）：
 *  - GET  /api/conversations/{id}  -> { ...会话字段, memberId, messages[], member{} }
 *  - POST /api/conversations/{id}/messages { content } -> { ok, message, moderation, blocked? }
 *  - POST /api/conversations/{id}/reply-suggest -> { suggestions: string[], source }
 *
 * 交互要点：
 *  - 本地先渲染乐观气泡，发送成功后以后端返回的 message 回写（保证 id/时间准确）。
 *  - 后端带内容审核（moderation）：命中拦截时如实提示，不静默吞掉。
 *  - AI 回复建议依赖会话上下文，仅在本页可用（不存在独立 AI 会话）。
 */
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { ref, nextTick } from 'vue'
import { api, type Conversation, type MessageItem, type Member } from '@/api'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

const convId = ref('')
const title = ref('会话')
const member = ref<Member | null>(null)
const messages = ref<MessageItem[]>([])
const loading = ref(false)
const loadError = ref('')

const inputText = ref('')
const sending = ref(false)
const scrollIntoId = ref('')

/* ---------- AI 回复建议 ---------- */
const suggestions = ref<string[]>([])
const showSuggest = ref(false)
const suggestLoading = ref(false)

const loadDetail = async () => {
  if (!convId.value) return
  loading.value = true
  loadError.value = ''
  try {
    const d: any = await api.conversationDetail(convId.value)
    messages.value = (d?.messages ?? []) as MessageItem[]
    member.value = (d?.member ?? null) as Member | null
    title.value = d?.name || title.value
    scrollToBottom()
  } catch (e: any) {
    messages.value = []
    loadError.value = e?.message || '会话加载失败'
  } finally {
    loading.value = false
  }
}

onLoad((q: any) => {
  convId.value = q?.id || ''
  if (q?.name) title.value = decodeURIComponent(q.name)
  loadDetail()
})

/* ---------- 工具 ---------- */
const isMine = (m: MessageItem) => m.sender === 'agent'
const isSystem = (m: MessageItem) => m.sender === 'system'
const fmtTime = (t?: string) => {
  if (!t) return ''
  // 后端返回 ISO 串（2026-09-01T14:30:07.48），取 HH:mm
  const m = String(t).match(/T(\d{2}):(\d{2})/)
  return m ? `${m[1]}:${m[2]}` : ''
}
const scrollToBottom = async () => {
  await nextTick()
  const last = messages.value[messages.value.length - 1]
  scrollIntoId.value = last?.id ? `msg-${last.id}` : ''
}
const memberBrief = () => {
  const m = member.value
  if (!m) return ''
  return [m.age ? `${m.age}岁` : '', m.city, m.job].filter(Boolean).join(' · ')
}

/* ---------- 发送 ---------- */
const onSend = async () => {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  sending.value = true
  // 乐观渲染：先上屏，失败则移除
  const tempId = `tmp-${Date.now()}`
  messages.value.push({
    id: tempId,
    sender: 'agent',
    content: text,
    type: 'text',
    createdAt: new Date().toISOString(),
    _pending: true,
  } as any)
  inputText.value = ''
  showSuggest.value = false
  scrollToBottom()

  try {
    const res: any = await api.sendMessage(convId.value, { content: text })
    // 后端拦截（内容审核未通过）：标记该条为失败并如实告知
    if (res?.blocked) {
      const t = messages.value.find((m: any) => m.id === tempId) as any
      if (t) t._failed = true
      const advice = res?.moderation?.advice?.[0]
      uni.showToast({ title: advice || '内容未通过合规审核，已阻止发送', icon: 'none', duration: 2600 })
      return
    }
    const saved = res?.message
    const idx = messages.value.findIndex((m: any) => m.id === tempId)
    if (saved && idx >= 0) messages.value[idx] = saved
    else if (saved) messages.value.push(saved)
    scrollToBottom()
  } catch (e: any) {
    const idx = messages.value.findIndex((m: any) => m.id === tempId)
    if (idx >= 0) (messages.value[idx] as any)._failed = true
    uni.showToast({ title: e?.message || '发送失败', icon: 'none' })
  } finally {
    sending.value = false
  }
}

/** 重发（针对失败或本地待发送的消息） */
const onRetry = async (m: any) => {
  const text = m.content
  const idx = messages.value.findIndex((x: any) => x.id === m.id)
  if (idx >= 0) messages.value.splice(idx, 1)
  inputText.value = text
  await onSend()
}

/* ---------- AI 建议 ---------- */
const onToggleSuggest = async () => {
  if (showSuggest.value) {
    showSuggest.value = false
    return
  }
  if (suggestions.value.length === 0) {
    suggestLoading.value = true
    try {
      const r: any = await api.replySuggest(convId.value)
      suggestions.value = r?.suggestions ?? []
      if (suggestions.value.length === 0) {
        uni.showToast({ title: '暂无建议，请先沟通几轮', icon: 'none' })
        return
      }
    } catch {
      uni.showToast({ title: 'AI 建议服务不可用', icon: 'none' })
      return
    } finally {
      suggestLoading.value = false
    }
  }
  showSuggest.value = true
  scrollToBottom()
}
const useSuggestion = (s: string) => {
  inputText.value = s
  showSuggest.value = false
}

/* ---------- 导航 ---------- */
const onBack = () => uni.navigateBack()
const onMemberTap = () => {
  if (member.value?.id) uni.navigateTo({ url: `/pages/member/detail?id=${member.value.id}` })
}
onUnload(() => {
  /* 离开页面无轮询任务，占位以保证生命周期完整 */
})

/* ---------- 拨号（顶栏电话图标） ---------- */
const onCall = () => {
  const phone = member.value?.phone
  if (!phone) {
    uni.showToast({ title: '该会员未留手机号', icon: 'none' })
    return
  }
  uni.makePhoneCall({
    phoneNumber: String(phone),
    fail: () => {
      /* 用户取消或不支持拨号，静默返回 */
    },
  })
}
</script>

<template>
  <view class="page">
    <aurora-bg />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 自定义导航 -->
    <view class="glass-bar nav-bar">
      <view class="nav-back flex-center" hover-class="hover-opacity" @tap="onBack">
        <app-icon name="back-ink" size="36rpx" />
      </view>
      <view class="nav-center flex-center" @tap="onMemberTap">
        <text class="nav-title">{{ title }}</text>
        <text v-if="memberBrief()" class="nav-sub">{{ memberBrief() }}</text>
      </view>
      <view class="nav-right flex-center" hover-class="hover-opacity" @tap="onCall">
        <app-icon name="phone-primary" size="36rpx" />
      </view>
    </view>

    <!-- 消息区 -->
    <scroll-view
      class="msg-scroll"
      scroll-y
      :scroll-into-view="scrollIntoId"
      scroll-with-animation
      :enhanced="true"
      :show-scrollbar="false"
    >
      <view class="msg-inner">
        <!-- 加载失败 -->
        <view v-if="loadError" class="state-box flex-center">
          <text class="state-text">{{ loadError }}</text>
          <view class="state-btn flex-center" hover-class="hover-opacity" @tap="loadDetail">重试</view>
        </view>

        <!-- 空态：尚未沟通 -->
        <view v-else-if="!loading && messages.length === 0" class="state-box flex-center">
          <view class="state-icon flex-center">
            <app-icon name="message-primary" size="48rpx" />
          </view>
          <text class="state-title">还没有聊天记录</text>
          <text class="state-text">发一条消息，开启与「{{ title }}」的沟通</text>
        </view>

        <!-- 消息气泡 -->
        <template v-else>
          <view
            v-for="m in messages"
            :id="`msg-${m.id}`"
            :key="m.id"
            class="msg-row"
            :class="isSystem(m) ? 'row-system' : isMine(m) ? 'row-mine' : 'row-theirs'"
          >
            <!-- 系统提示 -->
            <text v-if="isSystem(m)" class="sys-text">{{ m.content }}</text>

            <template v-else>
              <view
                v-if="!isMine(m)"
                class="msg-avatar flex-center"
                :style="{ background: member?.avatarBg || 'linear-gradient(135deg,#E8C7D4,#8B254A)' }"
              >{{ (title || '会').slice(0, 1) }}</view>

              <view class="bubble-col">
                <view class="bubble" :class="isMine(m) ? 'bubble-mine' : 'bubble-theirs'">
                  <text class="bubble-text">{{ m.content }}</text>
                </view>
                <view class="bubble-meta">
                  <text class="bubble-time">{{ fmtTime(m.createdAt) }}</text>
                  <text v-if="(m as any)._failed" class="bubble-fail" @tap="onRetry(m)">发送失败 · 重发</text>
                  <text v-else-if="(m as any)._pending" class="bubble-pending">发送中…</text>
                </view>
              </view>
            </template>
          </view>
        </template>

        <view style="height: 24rpx" />
      </view>
    </scroll-view>

    <!-- AI 建议面板（浮在输入栏上方） -->
    <view v-if="showSuggest" class="suggest-panel">
      <view class="suggest-head flex-between items-center">
        <text class="suggest-title">AI 回复建议</text>
        <view class="flex-center" hover-class="hover-opacity" @tap="showSuggest = false">
          <app-icon name="close-ink" size="28rpx" />
        </view>
      </view>
      <view
        v-for="(s, i) in suggestions"
        :key="i"
        class="suggest-item"
        hover-class="hover-opacity"
        @tap="useSuggestion(s)"
      >
        <text class="suggest-text">{{ s }}</text>
      </view>
    </view>

    <!-- 输入栏 -->
    <view class="input-bar">
      <view class="input-ai flex-center" :class="{ 'input-ai-on': showSuggest }" hover-class="hover-opacity" @tap="onToggleSuggest">
        <app-icon name="sparkles-gold" size="36rpx" />
      </view>
      <input
        v-model="inputText"
        class="input-main"
        placeholder="输入消息…"
        placeholder-class="input-ph"
        confirm-type="send"
        :disabled="sending"
        @confirm="onSend"
      />
      <view
        class="send-btn flex-center"
        :class="{ 'send-off': !inputText.trim() || sending }"
        hover-class="hover-opacity"
        @tap="onSend"
      >
        <app-icon name="message-primary" size="32rpx" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  position: relative;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* ---------- 导航 ---------- */
.nav-bar {
  position: relative;
  z-index: 3;
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
}
.nav-back,
.nav-right {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}
.nav-center {
  flex: 1;
  flex-direction: column;
  gap: 2rpx;
  padding: 0 12rpx;
}
.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--q-ink-1);
  max-width: 400rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-sub {
  font-size: 22rpx;
  color: var(--q-ink-3);
}

/* ---------- 消息区 ---------- */
.msg-scroll {
  position: relative;
  z-index: 2;
  flex: 1;
  min-height: 0;
}
.msg-inner {
  padding: 16rpx 32rpx 0;
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}
.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
}
.row-mine {
  flex-direction: row-reverse;
}
.row-system {
  justify-content: center;
}
.sys-text {
  font-size: 22rpx;
  color: var(--q-ink-4);
  background: var(--q-glass-bg-light);
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
}
.msg-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  flex-shrink: 0;
}
.bubble-col {
  max-width: 68%;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.bubble {
  padding: 22rpx 28rpx;
  border-radius: 28rpx;
  box-shadow: var(--q-glass-shadow);
}
.bubble-mine {
  background: linear-gradient(135deg, var(--q-primary), #a52d58);
  color: #fff;
  border-bottom-right-radius: 8rpx;
}
.bubble-theirs {
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  color: var(--q-ink-1);
  border-bottom-left-radius: 8rpx;
}
.bubble-text {
  font-size: 28rpx;
  line-height: 1.6;
}
.bubble-meta {
  display: flex;
  gap: 12rpx;
  padding: 0 8rpx;
}
.row-mine .bubble-meta {
  justify-content: flex-end;
}
.bubble-time {
  font-size: 20rpx;
  color: var(--q-ink-4);
}
.bubble-pending {
  font-size: 20rpx;
  color: var(--q-ink-4);
}
.bubble-fail {
  font-size: 20rpx;
  color: var(--q-error);
}

/* ---------- 空态 / 错误态 ---------- */
.state-box {
  margin-top: 120rpx;
  flex-direction: column;
  gap: 16rpx;
}
.state-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: var(--q-primary-soft);
}
.state-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.state-text {
  font-size: 25rpx;
  color: var(--q-ink-3);
  text-align: center;
  line-height: 1.6;
}
.state-btn {
  margin-top: 12rpx;
  padding: 16rpx 48rpx;
  border-radius: 999rpx;
  background: var(--q-gold-gradient);
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
}

/* ---------- AI 建议面板 ---------- */
.suggest-panel {
  position: relative;
  z-index: 3;
  flex-shrink: 0;
  margin: 0 24rpx 12rpx;
  padding: 20rpx 24rpx;
  border-radius: 28rpx;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}
.suggest-head {
  margin-bottom: 12rpx;
}
.suggest-title {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--q-gold-dark);
}
.suggest-item {
  padding: 18rpx 20rpx;
  border-radius: 18rpx;
  background: var(--q-primary-soft);
  margin-bottom: 10rpx;
}
.suggest-text {
  font-size: 25rpx;
  color: var(--q-ink-1);
  line-height: 1.5;
}

/* ---------- 输入栏 ---------- */
.input-bar {
  position: relative;
  z-index: 3;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--q-glass-bg);
  border-top: 1px solid var(--q-glass-border);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
}
.input-ai {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--q-glass-bg-light);
  border: 1px solid var(--q-glass-border);
  flex-shrink: 0;
}
.input-ai-on {
  background: var(--q-gold-soft);
  border-color: rgba(212, 175, 55, 0.35);
}
.input-main {
  flex: 1;
  height: 76rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: var(--q-glass-bg-light);
  border: 1px solid var(--q-glass-border);
  font-size: 27rpx;
  color: var(--q-ink-1);
}
.input-ph {
  color: var(--q-ink-4);
}
.send-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--q-gold-gradient);
  flex-shrink: 0;
  box-shadow: 0 6rpx 16rpx rgba(139, 37, 74, 0.18);
}
.send-off {
  opacity: 0.45;
}

.hover-opacity {
  opacity: 0.6;
}
</style>
