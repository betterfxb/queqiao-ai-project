<script setup lang="ts">
/** 会员档案编辑 · 服务信息表单（等级 / 红娘 / 状态 / 标签云 / 简介） */
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { MemberForm } from '../types'

const props = defineProps<{ form: MemberForm; tags: string[]; agents?: string[] }>()
const agentList = () => (props.agents?.length ? props.agents : props.form.agent ? [props.form.agent] : [])

const formRef = ref<FormInstance>()
const rules: FormRules<MemberForm> = {
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的 11 位手机号', trigger: 'blur' },
  ],
  level: [{ required: true, message: '请选择会员等级', trigger: 'change' }],
  status: [{ required: true, message: '请选择会员状态', trigger: 'change' }],
}

defineExpose({ validate: () => formRef.value?.validate() })

const emit = defineEmits<{
  (e: 'add-tag', tag: string): void
  (e: 'remove-tag', tag: string): void
}>()

const tagInput = ref('')
const onAdd = () => {
  emit('add-tag', tagInput.value)
  tagInput.value = ''
}
</script>

<template>
  <GlassCard class="form-card" padding="">
    <div class="section-title"><span class="section-title-bar" />服务信息</div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item class="form-item" label="会员等级" prop="level">
          <el-select v-model="form.level">
            <el-option v-for="l in ['普通','VIP','SVIP','金卡','钻石VIP']" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" label="服务红娘" prop="agent">
          <el-select v-model="form.agent" filterable allow-create default-first-option placeholder="选择或输入红娘">
            <el-option v-for="a in agentList()" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" label="会员状态" prop="status">
          <el-select v-model="form.status">
            <el-option v-for="s in ['待审核','认证中','已认证','牵线中','匹配中','已约见','已暂停']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item class="form-item span2" label="标签">
          <div class="tag-cloud">
            <el-tag v-for="t in tags" :key="t" closable @close="emit('remove-tag', t)" class="member-tag">{{ t }}</el-tag>
            <el-input v-model="tagInput" class="tag-input" placeholder="+ 添加标签" @keyup.enter="onAdd" />
          </div>
        </el-form-item>
        <el-form-item class="form-item span2" label="会员简介" prop="bio">
          <el-input v-model="form.bio" type="textarea" :rows="3" placeholder="一句话介绍这位会员" />
        </el-form-item>
      </div>
    </el-form>
  </GlassCard>
</template>

<style scoped>
.form-card { padding: 24px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 20px; margin-top: 18px; }
.form-item { position: relative; }
.form-item :deep(.el-form-item__label) { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-2); margin-bottom: 6px; }
.form-item :deep(.el-form-item) { margin-bottom: 0; }
.form-item.span2 { grid-column: span 2; }
.form-item .el-input, .form-item .el-select { width: 100%; }
.tag-cloud { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.member-tag { background: var(--dt-primary-soft-2); color: var(--dt-primary); border: none; font-weight: 600; }
.tag-input { width: 120px; }
</style>
