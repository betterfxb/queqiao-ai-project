<script setup lang="ts">
/**
 * ActivityHero — 精选活动 Hero 横幅（左侧日期封面 + 右侧活动详情）
 */
import { computed } from 'vue'
import type { Activity } from '@/api'

const props = defineProps<{ activity: Activity }>()
defineEmits<{ (e: 'detail'): void; (e: 'edit'): void }>()

const pct = computed(() => Math.round((props.activity.signed / props.activity.capacity) * 100))
</script>

<template>
  <div class="glass-card hero-card">
    <div class="hero-cover">
      <div class="hero-month">AUGUST<br>2026</div>
      <div class="hero-date">{{ activity.date }}</div>
      <div class="hero-time">{{ activity.weekday }} {{ activity.time }}</div>
    </div>
    <div class="hero-info">
      <div class="hero-badges">
        <span class="status-pill gold"><span class="dot" />{{ activity.status }}</span>
        <span class="hero-star">重点推荐活动</span>
      </div>
      <div class="hero-title">{{ activity.title }}</div>
      <div class="hero-tags">
        <span v-for="t in activity.tags" :key="t" class="hero-tag">{{ t }}</span>
      </div>
      <div class="hero-stats">
        <span>已报名 <b>{{ activity.signed }}/{{ activity.capacity }}</b></span>
        <span>女 {{ activity.female }} · 男 {{ activity.male }}</span>
        <span>费用 <b>¥{{ activity.price }}/人</b></span>
        <span><app-icon name="map-pin" :size="12" />{{ activity.place }}</span>
      </div>
      <div class="hero-progress">
        <div class="hero-progress-track"><div class="hero-progress-fill" :style="{ width: pct + '%' }" /></div>
        <span>{{ pct }}%</span>
      </div>
      <div class="hero-actions">
        <button class="btn btn-primary btn-sm" @click="$emit('detail')">查看详情</button>
        <button class="btn btn-outline btn-sm" @click="$emit('edit')">编辑活动</button>
        <div style="flex:1" />
        <span class="rsvp female">女 {{ activity.female }}</span>
        <span class="rsvp male">男 {{ activity.male }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero-card { display: flex; overflow: hidden; padding: 0; }
.hero-cover {
  width: 220px; flex-shrink: 0; background: var(--dt-primary); color: #fff;
  display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 6px; padding: 24px;
}
.hero-month { font-size: 0.8125rem; text-align: center; color: var(--dt-gold-lighter); }
.hero-date { font-size: 1.75rem; font-weight: 600; border: 2px solid var(--dt-gold-light); border-radius: var(--dt-radius-lg); padding: 4px 14px; }
.hero-time { font-size: 0.8125rem; color: var(--dt-gold-lighter); }
.hero-info { flex: 1; padding: 22px 24px; }
.hero-badges { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.hero-star { font-size: 0.75rem; color: var(--dt-gold-dark); font-weight: 600; }
.hero-title { font-size: 1.25rem; font-weight: 600; color: var(--dt-ink-1); margin-bottom: 10px; }
.hero-tags { display: flex; gap: 8px; margin-bottom: 14px; }
.hero-tag { font-size: 0.6875rem; padding: 3px 10px; border-radius: var(--dt-radius-sm); background: var(--dt-gold-soft); color: var(--dt-gold-dark); font-weight: 600; }
.hero-tag:nth-child(2) { background: var(--dt-primary-soft-2); color: var(--dt-primary); }
.hero-stats { display: flex; gap: 18px; font-size: 0.8125rem; color: var(--dt-ink-3); margin-bottom: 12px; flex-wrap: wrap; }
.hero-stats b { color: var(--dt-ink-1); }
.hero-stats span { display: inline-flex; align-items: center; gap: 3px; }
.hero-progress { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.hero-progress-track { flex: 1; height: 8px; border-radius: var(--dt-radius-sm); background: var(--dt-muted); overflow: hidden; }
.hero-progress-fill { height: 100%; border-radius: var(--dt-radius-sm); background: var(--dt-primary); }
.hero-progress span { font-size: 0.75rem; font-weight: 600; color: var(--dt-gold-dark); }
.hero-actions { display: flex; align-items: center; gap: 10px; }
.rsvp { font-size: 0.75rem; font-weight: 600; padding: 3px 12px; border-radius: var(--dt-radius-sm); }
.rsvp.female { background: rgba(168, 96, 122, 0.12); color: #a8607a; }
.rsvp.male { background: var(--dt-info-soft); color: var(--dt-info); }
</style>
