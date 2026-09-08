<script setup lang="ts">
/** 情感 / 性格测评（1-B）：管理员 / 红娘代会员作答，产出契合标签写入档案 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchQuizTemplates,
  submitMemberQuiz,
  fetchMemberQuiz,
  type QuizTemplate,
  type QuizResult,
  type MemberQuizHistory,
} from '@/api/modules/quiz'

const route = useRoute()
const router = useRouter()
const memberId = (route.query.memberId as string) || ''
const memberName = (route.query.name as string) || ''

const templates = ref<QuizTemplate[]>([])
const loading = ref(false)
const activeId = ref('')
const answers = ref<Record<string, string>>({})
const submitting = ref(false)
const result = ref<QuizResult | null>(null)
const history = ref<MemberQuizHistory | null>(null)

const active = computed<QuizTemplate | null>(
  () => templates.value.find((t) => t.id === activeId.value) ?? templates.value[0] ?? null,
)

const allAnswered = computed(() => {
  if (!active.value) return false
  return active.value.questions.every((q) => answers.value[q.id])
})

async function load() {
  loading.value = true
  try {
    templates.value = await fetchQuizTemplates()
    if (active.value) activeId.value = active.value.id
    if (memberId) history.value = await fetchMemberQuiz(memberId)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载测评模板失败')
  } finally {
    loading.value = false
  }
}

function choose(qid: string, key: string) {
  answers.value[qid] = key
}

async function submit() {
  if (!memberId) {
    ElMessage.warning('请先从会员档案进入测评')
    return
  }
  if (!active.value) {
    ElMessage.warning('暂无可用的测评模板')
    return
  }
  if (!allAnswered.value) {
    ElMessage.warning('请完成所有题目再提交')
    return
  }
  submitting.value = true
  try {
    result.value = await submitMemberQuiz(memberId, {
      templateId: active.value.id,
      answers: { ...answers.value },
    })
    ElMessage.success('测评完成，契合标签已写入会员档案')
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function reset() {
  answers.value = {}
  result.value = null
}

onMounted(load)
</script>

<template>
  <div class="quiz-page">
    <el-card class="quiz-card" shadow="never">
      <template #header>
        <div class="quiz-head">
          <div>
            <div class="quiz-title">情感性格测评</div>
            <div class="quiz-sub">为会员生成契合标签，用于匹配推荐解释</div>
          </div>
          <el-button text type="primary" @click="router.push('/members')">返回会员管理</el-button>
        </div>
      </template>

      <div v-if="memberId" class="quiz-member">
        当前会员：<b>{{ memberName || memberId }}</b>
        <span class="quiz-member-id">{{ memberId }}</span>
        <el-tag v-if="history?.done" size="small" type="success" effect="plain" class="quiz-hist">
          已有测评记录（重新提交将更新标签）
        </el-tag>
      </div>
      <el-alert
        v-else
        type="warning"
        :closable="false"
        title="未指定会员"
        description="请从「会员管理 → 会员档案抽屉 → 情感测评」进入，以便将结果写入对应会员档案。"
      />

      <div v-loading="loading" class="quiz-body">
        <el-empty v-if="!loading && !active" description="暂无可用的测评模板" />

        <template v-else-if="active">
          <el-radio-group v-if="templates.length > 1" v-model="activeId" class="quiz-tpl-switch">
            <el-radio-button v-for="t in templates" :key="t.id" :value="t.id">{{ t.title }}</el-radio-button>
          </el-radio-group>

          <p class="quiz-desc">{{ active.description }}</p>

          <div v-for="(q, i) in active.questions" :key="q.id" class="quiz-q">
            <div class="quiz-q-text">{{ i + 1 }}. {{ q.text }}</div>
            <el-radio-group :model-value="answers[q.id]" @update:model-value="(v: string) => choose(q.id, v)">
              <el-radio v-for="o in q.options" :key="o.key" :value="o.key" border class="quiz-opt">
                {{ o.label }}
              </el-radio>
            </el-radio-group>
          </div>

          <div class="quiz-actions">
            <el-button @click="reset">清空</el-button>
            <el-button type="primary" :loading="submitting" :disabled="!allAnswered" @click="submit">
              提交测评
            </el-button>
          </div>
        </template>
      </div>

      <!-- 结果 -->
      <el-divider v-if="result" />
      <div v-if="result" class="quiz-result">
        <div class="quiz-result-title">测评结果 · {{ result.templateTitle }}</div>

        <div class="quiz-tags">
          <el-tag v-for="t in result.tags" :key="t" type="primary" effect="dark" round>{{ t }}</el-tag>
        </div>

        <div class="quiz-scores">
          <div v-for="s in result.scores" :key="s.key" class="quiz-score-row">
            <span class="quiz-score-name">{{ s.name }}</span>
            <div class="quiz-score-bar">
              <div
                class="quiz-score-fill"
                :class="s.score > 0 ? 'pos' : 'neg'"
                :style="{ width: Math.min(100, Math.abs(s.score) * 40) + '%' }"
              />
            </div>
            <span class="quiz-score-tag">{{ s.tag }}</span>
          </div>
        </div>

        <p class="quiz-summary">{{ result.summary }}</p>
        <p class="quiz-member-tags">会员档案标签：{{ result.memberTags.join('、') || '—' }}</p>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.quiz-page { padding: 20px; max-width: 880px; margin: 0 auto; }
.quiz-head { display: flex; align-items: center; justify-content: space-between; }
.quiz-title { font-size: 1.125rem; font-weight: 800; color: var(--dt-ink-1); }
.quiz-sub { font-size: 0.8125rem; color: var(--dt-ink-3); margin-top: 2px; }
.quiz-member { margin-bottom: 14px; font-size: 0.875rem; color: var(--dt-ink-2); }
.quiz-member b { color: var(--dt-ink-1); }
.quiz-member-id { margin-left: 8px; color: var(--dt-ink-4); font-size: 0.75rem; }
.quiz-hist { margin-left: 10px; }
.quiz-tpl-switch { margin-bottom: 12px; }
.quiz-desc { color: var(--dt-ink-3); font-size: 0.8125rem; margin: 6px 0 18px; }
.quiz-q { margin-bottom: 20px; }
.quiz-q-text { font-weight: 600; color: var(--dt-ink-1); margin-bottom: 10px; }
.quiz-opt { margin: 0 10px 10px 0; height: auto; padding: 10px 14px; white-space: normal; }
.quiz-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 8px; }
.quiz-result-title { font-weight: 800; color: var(--dt-ink-1); margin-bottom: 12px; }
.quiz-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
.quiz-scores { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.quiz-score-row { display: flex; align-items: center; gap: 12px; }
.quiz-score-name { width: 76px; color: var(--dt-ink-2); font-size: 0.8125rem; flex-shrink: 0; }
.quiz-score-bar { flex: 1; height: 10px; border-radius: 999px; background: var(--dt-muted); overflow: hidden; }
.quiz-score-fill { height: 100%; border-radius: 999px; }
.quiz-score-fill.pos { background: linear-gradient(90deg, #7a2145, #c8a26e); }
.quiz-score-fill.neg { background: linear-gradient(90deg, #5a7a9a, #b8c4d0); }
.quiz-score-tag { width: 72px; text-align: right; font-size: 0.8125rem; color: var(--dt-primary); font-weight: 600; flex-shrink: 0; }
.quiz-summary { color: var(--dt-ink-2); line-height: 1.7; margin: 0 0 6px; }
.quiz-member-tags { color: var(--dt-ink-4); font-size: 0.8125rem; margin: 0; }
</style>
