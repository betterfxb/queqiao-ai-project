<script setup lang="ts">
/**
 * 消息中心 · 聊天窗（栏2）
 * 消息流由父级通过 props 注入（已含真实历史）；发送 / AI 建议由父级处理，本组件只负责展示与输入。
 */
import { ref } from 'vue'
import type { Conversation } from '@/api'
import type { ChatMsg } from '@/api'
import VideoRoomDialog from './VideoRoomDialog.vue'

const props = defineProps<{
  conversation: Conversation
  messages: ChatMsg[]
  suggestions?: string[]
  aiLoading?: boolean
}>()

const emit = defineEmits<{
  (e: 'send', text: string): void
  (e: 'suggest'): void
}>()

const input = ref('')
const send = (text?: string) => {
  const t = (text ?? input.value).trim()
  if (!t) return
  emit('send', t)
  input.value = ''
}

const quickReplies = ['好的，我来安排', '请问方便什么时间？', '请稍等', '祝您牵手成功']

// 1-C 视频约见：打开视频房间弹窗
const showVideo = ref(false)
const videoMember = ref<{ id: string; name: string }>({ id: '', name: '' })
function openVideo() {
  const m = (props.conversation as any)?.member
  videoMember.value = { id: m?.id || '', name: props.conversation.name }
  showVideo.value = true
}
</script>

<template>
  <div class="chat-col glass-card">
    <div class="chat-head">
      <div class="conv-avatar member">{{ conversation.name[0] }}</div>
      <div class="chat-head-info">
        <div class="chat-name">{{ conversation.name }} <level-badge :level="(conversation as any).member?.level || 'VIP'" /></div>
        <div class="chat-status"><span class="online-inline" />{{ conversation.online ? '在线' : '离线' }}</div>
      </div>
      <div class="chat-head-actions">
        <button class="icon-btn" title="AI 回复建议" :disabled="aiLoading" @click="emit('suggest')"><app-icon name="sparkles" :size="17" /></button>
        <button class="icon-btn"><app-icon name="phone" :size="17" /></button>
        <button class="icon-btn" title="发视频约见" @click="openVideo"><app-icon name="video" :size="17" /></button>
      </div>
    </div>

    <div class="chat-body">
      <template v-for="(m, i) in messages" :key="i">
        <div v-if="m.sender === 'system'" class="sys-msg">{{ m.content }}</div>
        <div v-else class="msg-row" :class="m.sender === 'agent' ? 'me' : 'them'">
          <div class="msg-bubble">{{ m.content }}</div>
          <div class="msg-meta">
            {{ m.createdAt ? m.createdAt.slice(11, 16) : '' }}
            <span v-if="m.sender === 'agent' && m.readFlag" class="read-mark">✓✓ 已读</span>
          </div>
        </div>
      </template>
      <div v-if="!messages.length" class="sys-msg">暂无消息，发送第一条开场白吧</div>
    </div>

    <div v-if="suggestions?.length" class="suggest-bar">
      <div class="suggest-title"><app-icon name="sparkles" :size="12" />AI 建议话术（点击发送）</div>
      <div class="suggest-chips">
        <span v-for="(s, i) in suggestions" :key="i" class="suggest-chip" @click="send(s)">{{ s }}</span>
      </div>
    </div>

    <div class="chat-input">
      <div class="quick-replies">
        <span v-for="q in quickReplies" :key="q" class="quick-chip" @click="send(q)">{{ q }}</span>
      </div>
      <div class="input-row">
        <button class="icon-btn"><app-icon name="paperclip" :size="17" /></button>
        <button class="icon-btn"><app-icon name="smile" :size="17" /></button>
        <input v-model="input" placeholder="输入消息..." @keyup.enter="send()" />
        <button class="send-btn" @click="send()"><app-icon name="send" :size="16" /></button>
      </div>
    </div>

    <VideoRoomDialog v-model="showVideo" :member-id="videoMember.id" :member-name="videoMember.name" />
  </div>
</template>

<style scoped>
.chat-col { flex: 1; min-width: 0; display: flex; flex-direction: column; overflow: hidden; }
.chat-head { display: flex; align-items: center; gap: 12px; padding: 14px 18px; border-bottom: 1px solid var(--dt-border); }
.conv-avatar { position: relative; width: 42px; height: 42px; border-radius: 50%; color: #fff; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: linear-gradient(135deg,#f4c6d0,#a8667d); }
.chat-head-info { flex: 1; }
.chat-name { font-weight: 700; color: var(--dt-ink-1); display: flex; align-items: center; gap: 8px; }
.chat-status { font-size: 0.75rem; color: var(--dt-success); display: flex; align-items: center; gap: 4px; }
.online-inline { width: 7px; height: 7px; border-radius: 50%; background: var(--dt-success); }
.chat-head-actions { display: flex; gap: 8px; }
.chat-body { flex: 1; overflow-y: auto; padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.sys-msg { text-align: center; font-size: 0.6875rem; color: var(--dt-ink-4); background: var(--dt-muted); border-radius: 999px; padding: 4px 12px; align-self: center; }
.msg-row { display: flex; flex-direction: column; max-width: 70%; }
.msg-row.them { align-self: flex-start; }
.msg-row.me { align-self: flex-end; align-items: flex-end; }
.msg-bubble { padding: 10px 14px; border-radius: 14px; font-size: 0.8125rem; line-height: 1.6; white-space: pre-wrap; }
.msg-row.them .msg-bubble { background: #fff; color: var(--dt-ink-1); border: 1px solid var(--dt-border); border-top-left-radius: 4px; }
.msg-row.me .msg-bubble { background: var(--dt-gold-gradient); color: #fff; border-top-right-radius: 4px; }
.msg-meta { font-size: 0.625rem; color: var(--dt-ink-4); margin-top: 4px; display: flex; gap: 6px; }
.read-mark { color: var(--dt-gold-dark); }
.suggest-bar { border-top: 1px solid var(--dt-border); padding: 8px 18px; background: var(--dt-gold-soft); }
.suggest-title { font-size: 0.6875rem; color: var(--dt-gold-dark); font-weight: 700; margin-bottom: 6px; display: flex; align-items: center; gap: 4px; }
.suggest-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.suggest-chip { font-size: 0.6875rem; padding: 4px 10px; border-radius: 999px; background: #fff; border: 1px solid var(--dt-border-gold); color: var(--dt-gold-dark); cursor: pointer; }
.suggest-chip:hover { background: var(--dt-gold); color: #fff; }
.chat-input { border-top: 1px solid var(--dt-border); padding: 12px 18px; }
.quick-replies { display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.quick-chip { font-size: 0.75rem; padding: 4px 12px; border-radius: 999px; border: 1px solid var(--dt-border-gold); color: var(--dt-gold-dark); cursor: pointer; background: var(--dt-gold-soft); }
.input-row { display: flex; align-items: center; gap: 8px; }
.input-row input { flex: 1; height: 40px; border: 1px solid var(--dt-input-border); border-radius: 999px; padding: 0 16px; outline: none; font-size: 0.8125rem; font-family: var(--dt-font); background: var(--dt-input); }
.send-btn { width: 40px; height: 40px; border-radius: 50%; border: none; background: var(--dt-gold-gradient); color: #fff; display: flex; align-items: center; justify-content: center; cursor: pointer; flex-shrink: 0; }
</style>
