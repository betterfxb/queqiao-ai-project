<script setup lang="ts">
/**
 * 消息中心 · 会员信息面板（栏3）
 * 真实数据来源：会话接口 /conversations/{id}（姓名/在线/最近消息/负责红娘）。
 * 匹配度 / 匹配对象需后端 /match/score（依赖双方 ID），当前会话无对方 ID，
 * 明确标注「待后端接口」，绝不写死 96% 等 misleading 数字。
 */
import { onMounted, ref, watch } from 'vue'
import type { Conversation } from '@/api'
import { fetchConversation, type ConversationDetail } from '@/api'

const props = defineProps<{ conversation: Conversation }>()

const detail = ref<ConversationDetail | null>(null)
const loading = ref(false)

async function load(id: string) {
  loading.value = true
  try {
    detail.value = await fetchConversation(id)
  } catch {
    // 接口失败保持 null，模板回退到「待后端接口」
    detail.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => load(props.conversation.id))
watch(
  () => props.conversation.id,
  (id) => {
    if (id) load(id)
  },
)
</script>

<template>
  <aside class="info-col">
    <div class="glass-card info-card profile">
      <div class="info-avatar-ring">
        <div class="info-avatar">{{ conversation.name.slice(0, 1) }}</div>
        <span class="match-corner match-pending">待接口</span>
      </div>
      <div class="info-name">{{ conversation.name }}</div>
      <div class="info-meta">
        {{ conversation.online ? '在线' : '离线' }} · {{ conversation.preview || '暂无消息' }}
      </div>
      <div class="info-match">匹配度待后端接口</div>
    </div>

    <div class="glass-card info-card">
      <div class="side-label">牵线信息</div>
      <div class="kv"><span>匹配对象</span><b>待后端接口</b></div>
      <div class="kv"><span>负责红娘</span><b>{{ detail?.agent || '待后端接口' }}</b></div>
      <div class="kv"><span>牵线阶段</span><b>待后端接口</b></div>
      <div class="flex gap-2 mt-3">
        <button class="btn btn-outline btn-sm" style="flex: 1">查看档案</button>
        <button class="btn btn-gold btn-sm" style="flex: 1">安排约见</button>
      </div>
    </div>

    <div class="glass-card info-card">
      <div class="side-label">共享资料</div>
      <div class="file-row text-muted">待后端接口提供</div>
    </div>

    <div class="glass-card info-card">
      <div class="side-label">最近互动</div>
      <template v-if="detail?.messages?.length">
        <div v-for="(m, i) in detail.messages.slice(-3)" :key="i" class="interact-row">
          <span class="i-dot" />
          {{ m.sender === 'member' ? '会员' : m.sender === 'agent' ? '红娘' : '系统' }}：{{ m.content }}
        </div>
      </template>
      <div v-else class="interact-row text-muted">暂无真实互动记录</div>
    </div>
  </aside>
</template>

<style scoped>
.info-col { width: 300px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; }
.info-card { padding: 18px; }
.info-card.profile { display: flex; flex-direction: column; align-items: center; text-align: center; }
.info-avatar-ring { position: relative; margin-bottom: 12px; }
.info-avatar { width: 76px; height: 76px; border-radius: 50%; background: linear-gradient(135deg,#f4c6d0,#a8667d); color: #fff; font-size: 1.875rem; font-weight: 700; display: flex; align-items: center; justify-content: center; border: 3px solid var(--dt-gold); }
.match-corner { position: absolute; bottom: -2px; right: -6px; background: var(--dt-gold-gradient); color: #fff; font-size: 0.6875rem; font-weight: 800; padding: 2px 8px; border-radius: 999px; border: 2px solid #fff; }
.match-corner.match-pending { background: var(--dt-ink-4); }
.info-name { font-size: 1.0625rem; font-weight: 800; color: var(--dt-ink-1); display: flex; align-items: center; gap: 8px; }
.info-meta { font-size: 0.75rem; color: var(--dt-ink-3); margin-top: 6px; }
.info-match { font-size: 0.75rem; color: var(--dt-gold-dark); background: var(--dt-gold-soft); border-radius: 999px; padding: 4px 12px; margin-top: 10px; }
.side-label { font-size: 0.8125rem; font-weight: 700; color: var(--dt-ink-1); margin-bottom: 12px; }
.kv { display: flex; justify-content: space-between; font-size: 0.8125rem; color: var(--dt-ink-3); padding: 5px 0; }
.kv b { color: var(--dt-ink-1); }
.file-row { display: flex; align-items: center; gap: 8px; font-size: 0.8125rem; color: var(--dt-ink-2); padding: 6px 0; }
.interact-row { display: flex; align-items: center; gap: 8px; font-size: 0.8125rem; color: var(--dt-ink-2); padding: 5px 0; }
.i-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--dt-gold); }
.text-muted { color: var(--dt-ink-4); }
</style>
