<script setup lang="ts">
/**
 * 消息中心 — 三栏聊天工作台：会话列表(真实) + 聊天窗(真实消息/发送) + 会员信息面板
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchConversations,
  fetchConversation,
  sendMessage,
  replySuggest,
  type Conversation,
  type ConversationDetail,
  type ChatMsg,
} from '@/api'
import { useRealtimeStore } from '@/stores/realtime'
import MessageToolbar from './components/MessageToolbar.vue'
import ConversationList from './components/ConversationList.vue'
import ChatPanel from './components/ChatPanel.vue'
import MemberInfoPanel from './components/MemberInfoPanel.vue'

const tabs = ['全部', '未读', '系统', '会员']
const filterTab = ref('全部')
const search = ref('')

const loading = ref(false)
const conversations = ref<Conversation[]>([])
const activeId = ref('')
const detail = ref<ConversationDetail | null>(null)
const suggestions = ref<string[]>([])
const aiLoading = ref(false)

const activeConv = computed(() => conversations.value.find((c) => c.id === activeId.value) ?? null)

// 实时接收当前会话的新消息（后端发送后通过 /topic/conversation/{id} 广播）
const realtimeStore = useRealtimeStore()
let convUnsub: (() => void) | null = null

function bindRealtime() {
  if (convUnsub) {
    convUnsub()
    convUnsub = null
  }
  if (!activeId.value) return
  convUnsub = realtimeStore.watchConversation(activeId.value, (payload) => {
    if (!payload.message) return
    const m = payload.message as unknown as ChatMsg
    if (!detail.value) detail.value = { id: activeId.value, messages: [] } as unknown as ConversationDetail
    detail.value.messages ??= []
    // 去重：不发件人自己刚本地追加的回声
    if (detail.value.messages.some((x) => (x as any).id && (x as any).id === (m as any).id)) return
    detail.value.messages.push(m)
    const item = conversations.value.find((c) => c.id === activeId.value)
    if (item) {
      item.preview = String((m as any).content ?? '')
      item.timeAgo = '刚刚'
    }
  })
}
watch(activeId, bindRealtime)
onUnmounted(() => {
  if (convUnsub) convUnsub()
})

const visibleConversations = computed(() =>
  conversations.value.filter((c) => {
    if (filterTab.value === '未读' && (!c.unread || c.unread <= 0)) return false
    if (filterTab.value === '系统' && c.kind !== 'system') return false
    if (filterTab.value === '会员' && c.kind !== 'member') return false
    if (search.value && !c.name.includes(search.value)) return false
    return true
  })
)

const loadConversations = async () => {
  loading.value = true
  try {
    conversations.value = await fetchConversations()
    if (!activeId.value && conversations.value.length) selectConversation(conversations.value[0])
  } catch (e: any) {
    ElMessage.error('加载会话失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

const selectConversation = async (c: Conversation) => {
  activeId.value = c.id
  suggestions.value = []
  try {
    detail.value = await fetchConversation(c.id)
  } catch (e: any) {
    ElMessage.error('加载消息失败：' + (e?.message ?? e))
  }
}

const doSend = async (text: string) => {
  if (!activeId.value) return
  try {
    const m: ChatMsg = await sendMessage(activeId.value, text)
    if (detail.value) (detail.value.messages ??= []).push(m)
    // 同步列表预览
    const item = conversations.value.find((c) => c.id === activeId.value)
    if (item) { item.preview = text; item.timeAgo = '刚刚' }
  } catch (e: any) {
    ElMessage.error('发送失败：' + (e?.message ?? e))
  }
}

const doSuggest = async () => {
  if (!activeId.value) return
  aiLoading.value = true
  try {
    const r = await replySuggest(activeId.value)
    suggestions.value = r.suggestions ?? []
    if (!suggestions.value.length) ElMessage.info('暂无建议，可主动发起沟通')
  } catch (e: any) {
    ElMessage.error('AI 建议失败：' + (e?.message ?? e))
  } finally {
    aiLoading.value = false
  }
}

onMounted(loadConversations)
</script>

<template>
  <div class="msg-page">
    <div class="conv-col glass-card">
      <MessageToolbar v-model:search="search" v-model:tab="filterTab" :tabs="tabs" />
      <ConversationList :items="visibleConversations" :active-id="activeId" @select="selectConversation" />
    </div>

    <ChatPanel
      v-if="activeConv"
      :conversation="activeConv"
      :messages="detail?.messages ?? []"
      :suggestions="suggestions"
      :ai-loading="aiLoading"
      @send="doSend"
      @suggest="doSuggest"
    />
    <div v-else class="chat-col glass-card chat-empty">请选择左侧会话开始沟通</div>

    <MemberInfoPanel v-if="activeConv" :conversation="activeConv" />
  </div>
</template>

<style scoped>
.msg-page { display: flex; gap: 20px; height: calc(100vh - 64px - 64px); }
.conv-col { width: 300px; flex-shrink: 0; display: flex; flex-direction: column; overflow: hidden; }
.chat-empty { flex: 1; display: flex; align-items: center; justify-content: center; color: var(--dt-ink-3); }
</style>
