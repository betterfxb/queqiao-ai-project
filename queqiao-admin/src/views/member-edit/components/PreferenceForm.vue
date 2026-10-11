<script setup lang="ts">
/** 会员档案编辑 · 择偶要求表单 */
import type { MemberForm } from '../types'

defineProps<{ form: MemberForm; isModified: (k: string) => boolean; matchCount: number }>()
const emit = defineEmits<{ (e: 'mark', key: string): void }>()
</script>

<template>
  <GlassCard class="form-card" padding="">
    <div class="section-title"><span class="section-title-bar" />择偶要求</div>
    <div class="form-grid">
      <div class="form-item span2">
        <label>年龄范围 <span class="range-val">{{ form.ageRange[0] }}-{{ form.ageRange[1] }} 岁</span></label>
        <el-slider v-model="form.ageRange" range :min="20" :max="50" />
        <div class="match-hint">当前条件下匹配人数：<b>{{ matchCount }}</b> 人</div>
      </div>
      <div class="form-item span2">
        <label>身高范围 <span class="range-val">{{ form.heightRange[0] }}-{{ form.heightRange[1] }} cm</span></label>
        <el-slider v-model="form.heightRange" range :min="150" :max="190" />
      </div>
      <div class="form-item"><label>学历要求</label>
        <el-select v-model="form.eduReq">
          <el-option v-for="e in ['不限','大专及以上','本科及以上','硕士及以上','博士']" :key="e" :label="e" :value="e" />
        </el-select>
      </div>
      <div class="form-item" :class="{ modified: isModified('cityReq') }">
        <label>所在城市</label><el-input v-model="form.cityReq" @input="emit('mark', 'cityReq')" />
      </div>
      <div class="form-item span2"><label>其他要求</label><el-input v-model="form.otherReq" type="textarea" :rows="3" /></div>
    </div>
  </GlassCard>
</template>

<style scoped>
.form-card { padding: 24px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px 20px; margin-top: 16px; }
.form-item { position: relative; }
.form-item.span2 { grid-column: span 2; }
.form-item label { display: flex; justify-content: space-between; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-2); margin-bottom: 6px; }
.form-item .el-input, .form-item .el-select { width: 100%; }
.form-item.modified :deep(.el-input__wrapper), .form-item.modified :deep(.el-select__wrapper) {
  border-color: var(--dt-primary); box-shadow: 0 0 0 1px var(--dt-primary);
}
.form-item.modified::after {
  content: ''; position: absolute; top: 0; right: 0; width: 0; height: 0;
  border-top: 14px solid var(--dt-primary); border-left: 14px solid transparent; border-radius: 0 var(--dt-radius-sm) 0 0;
}
.range-val { color: var(--dt-primary); font-weight: 600; }
.match-hint { font-size: 0.75rem; color: var(--dt-ink-3); margin-top: 4px; }
.match-hint b { color: var(--dt-primary); }
</style>
