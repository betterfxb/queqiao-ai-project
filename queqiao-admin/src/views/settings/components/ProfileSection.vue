<script setup lang="ts">
/**
 * 系统设置 · 个人资料卡（真实登录用户）
 * 数据：GET /api/auth/me  保存：PUT /api/auth/profile
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMe, updateProfile, type CurrentUser } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const me = ref<CurrentUser | null>(null)
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)

const form = reactive({ name: '', phone: '', email: '' })

const load = async () => {
  loading.value = true
  try {
    const u = await fetchMe()
    me.value = u
    form.name = u.name ?? ''
    form.phone = u.phone ?? ''
    form.email = u.email ?? ''
  } catch (e: any) {
    ElMessage.error('个人资料加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}
onMounted(load)

const initial = computed(() => (me.value?.name ?? '用').slice(0, 1))
const roleText = computed(() => {
  const r = me.value?.role ?? ''
  return { ADMIN: '超级管理员', MANAGER: '运营主管', AGENT: '红娘' }[r] ?? r ?? '—'
})
const maskPhone = (p?: string) =>
  p && p.length >= 11 ? p.slice(0, 3) + '****' + p.slice(7) : (p || '—')
const lastLogin = computed(() =>
  me.value?.lastLoginAt ? String(me.value.lastLoginAt).slice(0, 16).replace('T', ' ') : '—',
)

const save = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  saving.value = true
  try {
    const u = await updateProfile({ name: form.name.trim(), phone: form.phone.trim(), email: form.email.trim() })
    me.value = u
    auth.user = u.name
    try {
      sessionStorage.setItem('qq_user', u.name)
    } catch {
      /* ignore */
    }
    editing.value = false
    ElMessage.success('个人资料已更新')
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? '未知错误'))
  } finally {
    saving.value = false
  }
}

const cancel = () => {
  if (me.value) {
    form.name = me.value.name ?? ''
    form.phone = me.value.phone ?? ''
    form.email = me.value.email ?? ''
  }
  editing.value = false
}
</script>

<template>
  <!-- 个人资料卡 -->
  <div class="glass-card profile-card" v-loading="loading">
    <div class="avatar-wrap">
      <div class="profile-avatar" :style="me?.avatarBg ? { background: me.avatarBg } : undefined">
        {{ initial }}
      </div>
    </div>
    <div class="profile-info">
      <div class="profile-name-row">
        <span class="profile-name">{{ me?.name || '—' }}</span>
        <span class="role-badge">{{ roleText }}</span>
        <span class="uname">@{{ me?.username || '—' }}</span>
      </div>
      <div class="profile-contact">
        <span class="profile-contact-item">
          <app-icon name="mail" :size="14" /> {{ me?.email || '未绑定' }}
        </span>
        <span class="profile-contact-item">
          <app-icon name="phone" :size="14" /> {{ maskPhone(me?.phone) }}
        </span>
        <span class="profile-contact-item">
          <app-icon name="clock" :size="14" /> 上次登录 {{ lastLogin }}
        </span>
      </div>
    </div>
    <div class="profile-actions">
      <button v-if="!editing" class="btn btn-outline btn-sm" @click="editing = true">
        <app-icon name="edit" :size="14" /> 编辑资料
      </button>
      <template v-else>
        <button class="btn btn-ghost btn-sm" @click="cancel">取消</button>
        <button class="btn btn-gold btn-sm" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
      </template>
    </div>
  </div>

  <!-- 编辑表单 -->
  <section v-if="editing" class="glass-card setting-card">
    <section-header title="账号信息" />
    <el-form :model="form" label-width="88px" class="form-grid">
      <el-form-item label="姓名">
        <el-input v-model="form.name" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="账号">
        <el-input :model-value="me?.username" disabled />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="form.phone" placeholder="11 位手机号" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" placeholder="name@example.com" />
      </el-form-item>
      <el-form-item label="角色">
        <el-input :model-value="roleText" disabled />
      </el-form-item>
      <el-form-item label="用户编号">
        <el-input :model-value="me?.id" disabled />
      </el-form-item>
    </el-form>
  </section>
</template>

<style scoped>
.profile-card {
  display: flex; align-items: center; gap: 20px;
  padding: 22px 24px;
}
.avatar-wrap { position: relative; flex-shrink: 0; }
.profile-avatar {
  width: 76px; height: 76px; border-radius: var(--dt-radius-lg);
  background: #8d847c; color: #fff;
  font-size: 1.75rem; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.profile-info { flex: 1; min-width: 0; }
.profile-name-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.profile-name { font-size: 1.125rem; font-weight: 600; color: var(--dt-ink-1); }
.uname { font-size: 0.7813rem; color: var(--dt-ink-3); }
.role-badge {
  padding: 3px 10px; border-radius: var(--dt-radius-sm);
  font-size: 0.75rem; font-weight: 600; color: #fff;
  background: var(--dt-primary);
}
.profile-contact { display: flex; gap: 18px; margin-top: 8px; flex-wrap: wrap; }
.profile-contact-item {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 0.8125rem; color: var(--dt-ink-3);
}
.profile-actions { display: flex; gap: 10px; flex-shrink: 0; }

.setting-card { padding: 22px 24px; }
.setting-card :deep(.section-header) { margin-bottom: 18px; }
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 28px;
}

@media (max-width: 1100px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>
