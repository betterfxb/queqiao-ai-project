<script setup lang="ts">
/**
 * 牵线 · 智能推荐模式
 * 由 /match?member=xxx 进入：展示目标会员 + 后端 /match/recommendations 返回的候选人，
 * 点击「建立牵线」直接调用 POST /pairs。
 */
import { matchRingBg } from '@/utils/member'

defineProps<{ target: any; list: any[]; loading?: boolean }>()
const emit = defineEmits<{
  (e: 'pick', candidate: any): void
  (e: 'back'): void
}>()
</script>

<template>
  <div v-loading="loading" class="rec-wrap">
    <GlassCard class="rec-head" padding="">
      <button class="btn btn-ghost" @click="emit('back')"><app-icon name="arrow-left" :size="15" />返回会员库</button>
      <template v-if="target">
        <BaseAvatar :name="target.name" :bg="target.avatarBg" :size="52" />
        <div>
          <div class="rec-name">{{ target.name }} <span class="rec-sub">{{ target.gender }} · {{ target.age }}岁 · {{ target.city }}</span></div>
          <div class="rec-sub">{{ target.education }} · {{ target.job }} · {{ target.income || '收入未填' }}</div>
        </div>
        <LevelBadge :level="target.level" />
      </template>
      <div style="flex:1" />
      <span class="rec-count">AI 推荐候选 {{ list.length }} 人</span>
    </GlassCard>

    <div v-if="list.length" class="rec-grid">
      <GlassCard v-for="r in list" :key="r.member.id" class="rec-card" padding="">
        <div class="rc-head">
          <BaseAvatar :name="r.member.name" :bg="r.member.avatarBg" :size="46" />
          <div class="rc-title">
            <div class="rc-name">{{ r.member.name }}<span class="rc-age">{{ r.member.age }}岁</span></div>
            <LevelBadge :level="r.member.level" />
          </div>
          <div class="rc-ring" :style="{ background: matchRingBg(r.score) }">
            <div class="rc-ring-in">{{ r.score }}</div>
          </div>
        </div>
        <div class="rc-info">
          <span>{{ r.member.city }}</span><i>·</i>
          <span>{{ r.member.education }}</span><i>·</i>
          <span>{{ r.member.job }}</span>
        </div>
        <ul class="rc-reasons">
          <li v-for="(x, i) in (r.reasons || []).slice(0, 3)" :key="i">{{ x }}</li>
        </ul>
        <div class="rc-dims" v-if="r.dimensions">
          <span v-for="(v, k) in r.dimensions" :key="k" class="rc-dim">{{ k }} {{ v }}%</span>
        </div>
        <div class="rc-semantic" v-if="r.semanticKeywords && r.semanticKeywords.length">
          <span class="rc-semantic-score">语义契合 {{ r.semanticScore }}%</span>
          <span class="rc-semantic-kw">{{ (r.semanticKeywords || []).slice(0, 4).join('、') }}</span>
        </div>
        <button class="btn btn-gold rc-btn" @click="emit('pick', r.member)">
          <app-icon name="heart" :size="15" />建立牵线
        </button>
      </GlassCard>
    </div>

    <el-empty v-else-if="!loading" description="暂无合适候选人，可放宽会员筛选条件后重试" />
  </div>
</template>

<style scoped>
.rec-wrap { display: flex; flex-direction: column; gap: 20px; }
.rec-head { display: flex; align-items: center; gap: 14px; padding: 16px 20px; }
.rec-name { font-size: 1.0625rem; font-weight: 800; color: var(--dt-ink-1); }
.rec-sub { font-size: 0.75rem; color: var(--dt-ink-3); font-weight: 500; margin-left: 6px; }
.rec-count { font-size: 0.8125rem; color: var(--dt-gold-dark); font-weight: 700; }

.rec-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }
.rec-card { padding: 20px; display: flex; flex-direction: column; gap: 12px; }
.rc-head { display: flex; align-items: center; gap: 12px; }
.rc-title { display: flex; flex-direction: column; gap: 5px; }
.rc-name { font-size: 1rem; font-weight: 800; color: var(--dt-ink-1); }
.rc-age { font-size: 0.75rem; font-weight: 500; color: var(--dt-ink-3); margin-left: 6px; }
.rc-ring { margin-left: auto; width: 52px; height: 52px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.rc-ring-in { width: 42px; height: 42px; border-radius: 50%; background: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800; color: var(--dt-gold-dark); }
.rc-info { font-size: 0.75rem; color: var(--dt-ink-3); display: flex; gap: 6px; align-items: center; }
.rc-info i { color: var(--dt-ink-4); font-style: normal; }
.rc-reasons { margin: 0; padding-left: 16px; font-size: 0.75rem; color: var(--dt-ink-2); line-height: 1.8; }
.rc-dims { display: flex; flex-wrap: wrap; gap: 6px; }
.rc-dim { font-size: 0.6875rem; padding: 2px 8px; border-radius: 999px; background: var(--dt-muted); color: var(--dt-ink-3); }
.rc-semantic { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 2px; }
.rc-semantic-score { font-size: 0.6875rem; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: var(--dt-gold, #d8a44a); color: #fff; }
.rc-semantic-kw { font-size: 0.75rem; color: var(--dt-ink-2); }
.rc-btn { width: 100%; }
</style>
