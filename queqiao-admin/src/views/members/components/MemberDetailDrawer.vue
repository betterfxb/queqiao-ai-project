<script setup lang="ts">
/** 会员管理 · 快速查看抽屉 */
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import type { Member } from '@/api'
import { fetchMemberPoints, type MemberPoints as MemberPointsVO } from '@/api'

const props = defineProps<{ member: Member | null }>()
const emit = defineEmits<{
  (e: 'match', row: Member): void
  (e: 'message', row: Member): void
  (e: 'card', row: Member): void
}>()

const router = useRouter()

/** 会员档案标签（含情感测评产出的契合标签） */
const memberTags = computed(() => {
  if (!props.member?.tags) return []
  return props.member.tags.split(',').map((s) => s.trim()).filter(Boolean)
})

/** 跳转到情感测评页，带入会员身份 */
function goQuiz() {
  if (!props.member) return
  router.push({ path: '/members/quiz', query: { memberId: props.member.id, name: props.member.name } })
}

const visible = defineModel<boolean>({ required: true })

/** 与 MemberTable 保持一致的本页专用状态色映射 */
type PillType = 'success' | 'warning' | 'error' | 'info' | 'active' | 'gold' | 'neutral'
const pillType = (s: Member['status']): PillType =>
  s === '已认证' ? 'success' : s === '待审核' ? 'warning' : s === '认证中' ? 'info' : s === '已暂停' ? 'neutral' : 'active'

/** 积分概览（随会员切换实时拉取） */
const points = ref<MemberPointsVO | null>(null)
const loadingPoints = ref(false)

watch(
  () => props.member?.id,
  async (id) => {
    if (!id) {
      points.value = null
      return
    }
    loadingPoints.value = true
    try {
      points.value = await fetchMemberPoints(id)
    } catch {
      points.value = null
    } finally {
      loadingPoints.value = false
    }
  },
  { immediate: true },
)

const fmtTime = (t?: string | null) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—')
</script>

<template>
  <el-drawer v-model="visible" size="380px" :with-header="false" class="qv-drawer">
    <div v-if="member" class="qv-body">
      <BaseAvatar :name="member.name" :bg="member.avatarBg" :size="76" />
      <div class="qv-name">{{ member.name }}</div>
      <div class="qv-id">{{ member.id }}</div>
      <div class="qv-badges">
        <LevelBadge :level="member.level" />
        <StatusPill :type="pillType(member.status)" :text="member.status" />
      </div>

      <!-- 积分 / 等级卡片 -->
      <div class="qv-points" v-if="!loadingPoints && points">
        <div class="qv-points-main">
          <span class="qv-level">{{ points.level }}</span>
          <span class="qv-total">{{ points.total }} <i>分</i></span>
        </div>
        <div class="qv-points-logs" v-if="points.logs.length">
          <div class="qv-log" v-for="l in points.logs.slice(0, 3)" :key="l.id">
            <span class="qv-log-label">{{ l.typeLabel }}</span>
            <span class="qv-log-delta">+{{ l.delta }}</span>
            <span class="qv-log-time">{{ fmtTime(l.createdAt) }}</span>
          </div>
        </div>
        <div class="qv-points-empty" v-else>暂无积分记录</div>
      </div>
      <div class="qv-points qv-points-loading" v-else-if="loadingPoints">
        <app-icon name="loader" :size="16" class="spin" />
      </div>
      <div class="qv-list">
        <div class="qv-row"><span>性别 / 年龄</span><b>{{ member.gender }} · {{ member.age }}岁</b></div>
        <div class="qv-row"><span>所在地</span><b>{{ member.city }}</b></div>
        <div class="qv-row"><span>负责红娘</span><b>{{ member.agent }}</b></div>
        <div class="qv-row"><span>学历 / 职业</span><b>{{ member.education }} · {{ member.job }}</b></div>
        <div v-if="member.height || member.income" class="qv-row">
          <span>身高 / 年收入</span><b>{{ member.height ? member.height + 'cm' : '—' }} · {{ member.income || '—' }}</b>
        </div>
        <div v-if="member.house || member.car" class="qv-row">
          <span>房产 / 购车</span><b>{{ member.house || '—' }} · {{ member.car || '—' }}</b>
        </div>
        <div class="qv-row"><span>最近登录</span><b>{{ member.lastActive }}</b></div>
      </div>
      <div class="qv-stats">
        <div class="qv-stat"><div class="qv-stat-num">{{ member.matchCount }}</div><div class="qv-stat-label">牵线次数</div></div>
        <div class="qv-stat"><div class="qv-stat-num">{{ member.successRate }}%</div><div class="qv-stat-label">成功率</div></div>
      </div>
      <div class="qv-tags" v-if="memberTags.length">
        <span class="qv-tags-label">契合标签</span>
        <span class="qv-tag" v-for="t in memberTags" :key="t">{{ t }}</span>
      </div>
      <button class="btn btn-outline" style="width:100%;margin-bottom:10px" @click="goQuiz"><app-icon name="sparkles" :size="15" />情感测评</button>
      <button class="btn btn-outline" style="width:100%;margin-bottom:10px" @click="emit('message', member)"><app-icon name="message" :size="15" />发送消息</button>
      <button class="btn btn-outline" style="width:100%;margin-bottom:10px" @click="emit('card', member)"><app-icon name="award" :size="15" />生成会员卡片</button>
      <button class="btn btn-primary" style="width:100%" @click="emit('match', member)"><app-icon name="heart" :size="15" />发起牵线</button>
    </div>
  </el-drawer>
</template>

<style scoped>
.qv-body { display: flex; flex-direction: column; align-items: center; padding: 24px 20px; }
.qv-body > .app-avatar { background: #8d847c !important; }
.qv-name { font-size: 1.375rem; font-weight: 600; color: var(--dt-ink-1); margin-top: 12px; }
.qv-id { font-size: 0.8125rem; color: var(--dt-ink-4); }
.qv-badges { display: flex; gap: 8px; margin: 12px 0 16px; }
.qv-list { width: 100%; }
.qv-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--dt-border); font-size: 0.8125rem; color: var(--dt-ink-3); }
.qv-row b { color: var(--dt-ink-1); font-weight: 600; }
.qv-stats { display: flex; gap: 12px; width: 100%; margin: 16px 0; }
.qv-stat { flex: 1; background: var(--dt-muted); border-radius: var(--dt-radius-md); padding: 12px; text-align: center; }
.qv-stat-num { font-size: 1.375rem; font-weight: 600; color: var(--dt-primary); }
.qv-stat-label { font-size: 0.75rem; color: var(--dt-ink-3); }

/* 积分卡片 */
.qv-points {
  width: 100%;
  margin: 0 0 16px;
  padding: 16px;
  border-radius: var(--dt-radius-md);
  background: var(--dt-primary-soft);
  border: 1px solid var(--dt-border);
  box-sizing: border-box;
}
.qv-points-main { display: flex; align-items: baseline; justify-content: space-between; }
.qv-level {
  font-size: 0.8125rem; font-weight: 600; color: #fff;
  padding: 3px 10px; border-radius: var(--dt-radius-sm);
  background: var(--dt-primary);
}
.qv-total { font-size: 1.5rem; font-weight: 600; color: var(--dt-ink-1); }
.qv-total i { font-size: 0.8125rem; font-weight: 500; color: var(--dt-ink-3); font-style: normal; }
.qv-points-logs { margin-top: 10px; display: flex; flex-direction: column; gap: 6px; }
.qv-log { display: flex; align-items: center; gap: 8px; font-size: 0.75rem; color: var(--dt-ink-3); }
.qv-log-label { color: var(--dt-ink-2); font-weight: 600; }
.qv-log-delta { color: var(--dt-success); font-weight: 600; }
.qv-log-time { margin-left: auto; color: var(--dt-ink-4); }
.qv-points-empty, .qv-points-loading { text-align: center; color: var(--dt-ink-4); font-size: 0.8125rem; padding: 8px 0; }
.spin { animation: qv-spin 0.9s linear infinite; }
@keyframes qv-spin { to { transform: rotate(360deg); } }

/* 契合标签 */
.qv-tags { width: 100%; display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 16px; }
.qv-tags-label { font-size: 0.75rem; color: var(--dt-ink-3); }
.qv-tag {
  font-size: 0.75rem; font-weight: 600; color: var(--dt-primary);
  padding: 3px 10px; border-radius: var(--dt-radius-sm);
  background: var(--dt-primary-soft-2); border: 1px solid var(--dt-border);
}
</style>
