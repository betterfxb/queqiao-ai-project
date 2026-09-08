<script setup lang="ts">
/** 会员档案编辑 · 基本信息表单 */
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { MemberForm } from '../types'

const props = defineProps<{ form: MemberForm; isModified: (k: string) => boolean }>()
const emit = defineEmits<{ (e: 'mark', key: string): void }>()

const formRef = ref<FormInstance>()
const rules: FormRules<MemberForm> = {
  name: [{ required: true, message: '请输入会员姓名', trigger: 'blur' }],
  age: [
    { type: 'number', min: 18, max: 80, message: '年龄需在 18-80 之间', trigger: 'blur' },
  ],
  height: [
    { type: 'number', min: 120, max: 220, message: '身高需在 120-220cm 之间', trigger: 'blur' },
  ],
}

defineExpose({ validate: () => formRef.value?.validate() })
</script>

<template>
  <GlassCard class="form-card" padding="">
    <div class="section-title"><span class="section-title-bar" />基本信息</div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item class="form-item" :class="{ modified: isModified('name') }" label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="必填" />
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('gender') }" label="性别" prop="gender">
          <el-select v-model="form.gender"><el-option label="男" value="男" /><el-option label="女" value="女" /></el-select>
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('age') }" label="年龄" prop="age">
          <el-input v-model.number="form.age" type="number" />
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('height') }" label="身高 (cm)" prop="height">
          <el-input v-model.number="form.height" type="number" />
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('education') }" label="学历" prop="education">
          <el-select v-model="form.education">
            <el-option v-for="e in ['博士','硕士','本科','大专','高中'].concat([])" :key="e" :label="e" :value="e" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('job') }" label="职业" prop="job">
          <el-input v-model="form.job" @input="emit('mark', 'job')" />
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('income') }" label="年收入" prop="income">
          <el-select v-model="form.income" @change="emit('mark', 'income')">
            <el-option v-for="i in ['20-30万','30-50万','50-80万','80-120万','120万以上']" :key="i" :label="i" :value="i" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('city') }" label="所在城市" prop="city">
          <el-input v-model="form.city" />
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('hometown') }" label="籍贯" prop="hometown">
          <el-input v-model="form.hometown" />
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('marriage') }" label="婚姻状况" prop="marriage">
          <el-select v-model="form.marriage">
            <el-option v-for="m in ['未婚','离异','丧偶']" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('hasChild') }" label="子女情况" prop="hasChild">
          <el-select v-model="form.hasChild">
            <el-option v-for="c in ['无','有（同住）','有（不同住）']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('house') }" label="房产情况" prop="house">
          <el-select v-model="form.house">
            <el-option v-for="h in ['有房无贷','有房有贷','暂无']" :key="h" :label="h" :value="h" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-item" :class="{ modified: isModified('car') }" label="购车情况" prop="car">
          <el-select v-model="form.car">
            <el-option v-for="c in ['有车','暂无']" :key="c" :label="c" :value="c" />
          </el-select>
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
.form-item .el-input, .form-item .el-select { width: 100%; }
.form-item.modified :deep(.el-input__wrapper), .form-item.modified :deep(.el-select__wrapper) {
  border-color: var(--dt-gold); box-shadow: 0 0 0 1px var(--dt-gold);
}
.form-item.modified::after {
  content: ''; position: absolute; top: 0; right: 0; width: 0; height: 0;
  border-top: 14px solid var(--dt-gold); border-left: 14px solid transparent; border-radius: 0 6px 0 0;
}
</style>
