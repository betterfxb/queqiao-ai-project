<script setup lang="ts">
/**
 * 牵线反馈轻问卷（1-D 闭环）：满意度 + 卡点多选 + 备注。
 * 提交后写结构化反馈，并反哺会员偏好标签（影响后续匹配权重）。
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { pairFeedback } from '@/api'

const props = defineProps<{
  modelValue: boolean
  pairId: string
  side: 'male' | 'female' | 'both'
  memberName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'submitted'): void
}>()

const satisfaction = ref(0)
const blockers = ref<string[]>([])
const note = ref('')
const submitting = ref(false)

const BLOCKER_OPTIONS = [
  '异地顾虑', '节奏太快', '年龄差距', '经济差距', '学历差距',
  '沟通不畅', '家庭阻力', '三观不合', '性格不合',
]

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      satisfaction.value = 0
      blockers.value = []
      note.value = ''
    }
  },
)

const close = () => emit('update:modelValue', false)

const submit = async () => {
  if (!props.pairId) return
  submitting.value = true
  try {
    const s = satisfaction.value
    const kind = s >= 4 ? 'success' : s === 0 ? 'warning' : 'warning'
    const effectiveBlockers = blockers.value.filter((b) => b !== '暂无卡点')
    await pairFeedback(props.pairId, {
      side: props.side === 'both' ? 'agent' : props.side,
      text: note.value || (s >= 4 ? '反馈积极' : '存在顾虑'),
      kind: kind as any,
      satisfaction: s || undefined,
      blockers: effectiveBlockers,
    })
    ElMessage.success('反馈已提交，并已沉淀为会员偏好')
    emit('submitted')
    close()
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`记录反馈${memberName ? ' · ' + memberName : ''}`"
    width="460px"
    align-center
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="fb-form">
      <div class="fb-field">
        <label class="fb-label">整体满意度</label>
        <el-rate v-model="satisfaction" :max="5" show-score score-template="{value} 分" />
      </div>

      <div class="fb-field">
        <label class="fb-label">沟通卡点（可多选）</label>
        <el-checkbox-group v-model="blockers" class="fb-blockers">
          <el-checkbox-button v-for="o in BLOCKER_OPTIONS" :key="o" :value="o">{{ o }}</el-checkbox-button>
        </el-checkbox-group>
      </div>

      <div class="fb-field">
        <label class="fb-label">补充备注</label>
        <el-input
          v-model="note"
          type="textarea"
          :rows="3"
          placeholder="如：两人聊得投机，对异地问题比较敏感，建议优先同城对象"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交反馈</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.fb-form { display: flex; flex-direction: column; gap: 18px; padding: 4px 2px; }
.fb-field { display: flex; flex-direction: column; gap: 8px; }
.fb-label { font-size: 0.8125rem; color: var(--dt-ink-2); font-weight: 600; }
.fb-blockers { flex-wrap: wrap; }
</style>
