<script setup lang="ts">
/**
 * 系统设置 · 安全设置（#sec-security）
 * 策略项来自后端「安全设置」分类，父级统一保存；
 * 修改密码走真实接口 POST /api/auth/change-password。
 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword, type SettingItem } from '@/api'
import DynamicFields from './DynamicFields.vue'

defineProps<{
  items: SettingItem[]
  model: Record<string, string>
}>()

const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const openPwd = () => {
  pwd.oldPassword = ''
  pwd.newPassword = ''
  pwd.confirm = ''
  pwdVisible.value = true
}

const submitPwd = async () => {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写原密码与新密码')
    return
  }
  if (pwd.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (pwd.newPassword !== pwd.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdLoading.value = true
  try {
    await changePassword(pwd.oldPassword, pwd.newPassword)
    ElMessage.success('密码修改成功，下次登录请使用新密码')
    pwdVisible.value = false
  } catch (e: any) {
    ElMessage.error('修改失败：' + (e?.message ?? '原密码不正确'))
  } finally {
    pwdLoading.value = false
  }
}
</script>

<template>
  <section id="sec-security" class="glass-card setting-card">
    <section-header title="安全设置" />

    <!-- 真实安全策略项 -->
    <DynamicFields :items="items" :model="model" />

    <!-- 账号安全操作 -->
    <div class="security-list">
      <div class="security-row">
        <div class="security-row-left">
          <div class="security-icon"><app-icon name="lock" :size="20" /></div>
          <div class="security-info">
            <div class="security-label">登录密码</div>
            <div class="security-status">
              <span class="status-dot ok" />
              定期更换可降低账号被盗风险
            </div>
          </div>
        </div>
        <button class="btn btn-gold btn-sm" @click="openPwd">修改密码</button>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="修改登录密码" width="420px" append-to-body>
      <el-form label-width="82px">
        <el-form-item label="原密码">
          <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="pwd.confirm" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn btn-ghost" @click="pwdVisible = false">取消</button>
        <button class="btn btn-gold" :disabled="pwdLoading" @click="submitPwd">
          {{ pwdLoading ? '提交中…' : '确认修改' }}
        </button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.setting-card { padding: 22px 24px; }
.setting-card :deep(.section-header) { margin-bottom: 18px; }

.security-list { display: flex; flex-direction: column; margin-top: 18px; }
.security-row {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 16px 4px;
  border-top: 1px solid var(--dt-border);
}
.security-row-left { display: flex; align-items: center; gap: 14px; }
.security-icon {
  width: 42px; height: 42px; border-radius: var(--dt-radius-md);
  display: flex; align-items: center; justify-content: center;
  background: var(--dt-primary-soft-2); color: var(--dt-primary);
  flex-shrink: 0;
}
.security-label { font-size: 0.9063rem; font-weight: 600; color: var(--dt-ink-1); }
.security-status {
  display: inline-flex; align-items: center; gap: 7px;
  font-size: 0.8125rem; color: var(--dt-ink-3); margin-top: 3px;
}
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--dt-success); }
</style>
