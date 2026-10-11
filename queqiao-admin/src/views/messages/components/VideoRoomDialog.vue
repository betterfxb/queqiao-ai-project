<script setup lang="ts">
/** 视频相亲房间弹窗（1-C，MVP）：红娘主持三方，本地摄像头预览 + 房间号分发 + WS 信令 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createMeeting, closeMeeting } from '@/api/modules/meeting'
import { realtime } from '@/api/ws'

const props = defineProps<{
  memberId: string
  memberName: string
}>()
const visible = defineModel<boolean>({ required: true })

const roomId = ref('')
const wsTopic = ref('')
const signals = ref<{ type: string; text: string; at: string }[]>([])
const localVideo = ref<HTMLVideoElement | null>(null)
const localStream = ref<MediaStream | null>(null)
const loading = ref(false)
const unsub = ref<(() => void) | null>(null)

function describe(p: any): string {
  if (p?.type === 'room-created') return `房间已建立，等待 ${props.memberName || '会员'} 加入`
  if (p?.type === 'room-closed') return '房间已关闭'
  if (p?.type === 'peer-join') return `${p.peer || '对方'} 已加入`
  if (p?.type === 'peer-leave') return `${p.peer || '对方'} 已离开`
  return '收到实时信号'
}

function pushSignal(p: any) {
  signals.value.push({ type: p?.type || 'signal', text: describe(p), at: (p?.at || '').slice(11, 19) || '' })
}

async function startCamera() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    localStream.value = stream
    if (localVideo.value) localVideo.value.srcObject = stream
  } catch (e: any) {
    ElMessage.warning('无法访问摄像头：' + (e?.message || e))
  }
}

function stopCamera() {
  localStream.value?.getTracks().forEach((t) => t.stop())
  localStream.value = null
  if (localVideo.value) localVideo.value.srcObject = null
}

async function open() {
  if (!props.memberId) {
    ElMessage.warning('未获取到会员身份，无法发起视频房间')
    visible.value = false
    return
  }
  loading.value = true
  try {
    const res = await createMeeting({ memberA: props.memberId })
    roomId.value = res.roomId
    wsTopic.value = res.wsTopic
    pushSignal({ type: 'room-created', at: (res.createdAt || '').replace('T', ' ') })
    // 订阅房间实时信令（会员端小程序加入 / 离开时后端广播）
    unsub.value = realtime.subscribe(res.wsTopic, (p) => pushSignal(p))
    await startCamera()
  } catch (e: any) {
    ElMessage.error(e?.message || '发起视频房间失败')
    visible.value = false
  } finally {
    loading.value = false
  }
}

async function copyRoom() {
  if (!roomId.value) return
  try {
    await navigator.clipboard.writeText(roomId.value)
    ElMessage.success('房间号已复制，可发给会员小程序加入')
  } catch {
    ElMessage.warning('复制失败，请手动选择房间号')
  }
}

async function closeRoom() {
  if (roomId.value) {
    try {
      await closeMeeting(roomId.value)
    } catch {
      /* 关闭失败不影响本地退出 */
    }
  }
  cleanup()
  visible.value = false
}

function cleanup() {
  stopCamera()
  unsub.value?.()
  unsub.value = null
  roomId.value = ''
  wsTopic.value = ''
  signals.value = []
}

watch(
  () => visible.value,
  (v) => {
    if (v) open()
    else cleanup()
  },
)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="视频相亲房间"
    width="720px"
    :close-on-click-modal="false"
    @closed="cleanup"
  >
    <div v-loading="loading" class="vr">
      <div class="vr-host-tip">
        <app-icon name="sparkles" :size="14" />
        红娘主持三方视频 · 把房间号发给会员小程序即可加入；信令经实时通道推送。
      </div>

      <div class="vr-room">
        <span class="vr-room-label">房间号</span>
        <code class="vr-room-id">{{ roomId || '—' }}</code>
        <el-button size="small" :disabled="!roomId" @click="copyRoom">复制房间号</el-button>
      </div>

      <div class="vr-grid">
        <div class="vr-tile">
          <video ref="localVideo" autoplay playsinline muted class="vr-video" />
          <span class="vr-tile-tag">我（红娘）</span>
        </div>
        <div class="vr-tile vr-tile--remote">
          <video ref="remoteVideo" autoplay playsinline class="vr-video" />
          <span class="vr-tile-tag">{{ memberName || '会员' }}</span>
          <div v-if="!signals.some((s) => s.type === 'peer-join')" class="vr-remote-hint">
            等待会员加入…
          </div>
        </div>
      </div>

      <div class="vr-log">
        <div v-for="(s, i) in signals" :key="i" class="vr-log-row">
          <span class="vr-log-time">{{ s.at }}</span>
          <span class="vr-log-text">{{ s.text }}</span>
        </div>
        <div v-if="!signals.length" class="vr-log-empty">暂无信令</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">最小化</el-button>
      <el-button type="danger" :disabled="!roomId" @click="closeRoom">结束房间</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.vr { display: flex; flex-direction: column; gap: 12px; }
.vr-host-tip {
  display: flex; align-items: center; gap: 6px;
  font-size: 0.8125rem; color: var(--dt-gold-dark);
  background: var(--dt-gold-soft); border-radius: var(--dt-radius-md);
  padding: 8px 12px;
}
.vr-room { display: flex; align-items: center; gap: 10px; }
.vr-room-label { font-size: 0.8125rem; color: var(--dt-ink-3); }
.vr-room-id {
  font-family: monospace; font-size: 1rem; font-weight: 600; color: var(--dt-primary);
  background: var(--dt-muted); padding: 4px 12px; border-radius: var(--dt-radius-sm);
}
.vr-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.vr-tile {
  position: relative; aspect-ratio: 4 / 3; border-radius: var(--dt-radius-md);
  overflow: hidden; background: #1c1c28; border: 1px solid var(--dt-border);
}
.vr-video { width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); }
.vr-tile-tag {
  position: absolute; left: 8px; bottom: 8px; font-size: 0.75rem; color: #fff;
  background: rgba(0,0,0,0.5); padding: 2px 8px; border-radius: var(--dt-radius-sm);
}
.vr-remote-hint {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.7); font-size: 0.875rem;
}
.vr-log { background: var(--dt-muted); border-radius: var(--dt-radius-md); padding: 10px 12px; max-height: 140px; overflow-y: auto; }
.vr-log-row { display: flex; gap: 10px; font-size: 0.75rem; padding: 3px 0; }
.vr-log-time { color: var(--dt-ink-4); flex-shrink: 0; }
.vr-log-text { color: var(--dt-ink-2); }
.vr-log-empty { color: var(--dt-ink-4); font-size: 0.75rem; text-align: center; }
</style>
