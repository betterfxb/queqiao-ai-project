<script setup lang="ts">
/**
 * 系统设置 · 团队管理（真实可操作）
 * 数据：GET /api/users  新建：POST /api/users  更新：PUT /api/users/{id}  删除：DELETE /api/users/{id}
 * 角色：ADMIN / MANAGER / AGENT；状态：启用 / 停用。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchUsers,
  createUser,
  updateUser,
  deleteUser,
  type SysUserItem,
} from '@/api'

const list = ref<SysUserItem[]>([])
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const editingId = ref<string | null>(null)

const empty = (): Partial<SysUserItem> & { username: string; password: string } => ({
  username: '',
  name: '',
  role: 'AGENT',
  phone: '',
  email: '',
  agentId: '',
  password: '123456',
})
const form = reactive(empty())

const roleText = (r?: string) =>
  ({ ADMIN: '超级管理员', MANAGER: '运营主管', AGENT: '红娘' }[r ?? ''] ?? r ?? '—')
const statusType = (s?: string) => (s === '启用' ? 'success' : 'info')
const canWrite = computed(() => true) // 后端已做权限拦截；前端只负责展示

const load = async () => {
  loading.value = true
  try {
    list.value = await fetchUsers()
  } catch (e: any) {
    ElMessage.error('团队列表加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}
onMounted(load)

const openCreate = () => {
  editingId.value = null
  Object.assign(form, empty())
  dialog.value = true
}
const openEdit = (u: SysUserItem) => {
  editingId.value = u.id
  Object.assign(form, {
    username: u.username,
    name: u.name,
    role: u.role,
    phone: u.phone ?? '',
    email: u.email ?? '',
    agentId: u.agentId ?? '',
    password: '',
  })
  dialog.value = true
}

const submit = async () => {
  if (!form.username.trim()) {
    ElMessage.warning('登录账号不能为空')
    return
  }
  if (!editingId.value && !form.password.trim()) {
    ElMessage.warning('请设置初始密码')
    return
  }
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      username: form.username.trim(),
      name: form.name?.trim() || form.username.trim(),
      role: form.role,
      phone: form.phone?.trim(),
      email: form.email?.trim(),
      agentId: form.agentId?.trim() || undefined,
    }
    if (form.password?.trim()) payload.password = form.password.trim()
    if (editingId.value) {
      await updateUser(editingId.value, payload as Partial<SysUserItem> & { password?: string })
      ElMessage.success('账号已更新')
    } else {
      await createUser(payload as Partial<SysUserItem> & { username: string; password?: string })
      ElMessage.success('账号已创建（默认密码 123456）')
    }
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? '未知错误'))
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (u: SysUserItem) => {
  const next = u.status === '启用' ? '停用' : '启用'
  try {
    await updateUser(u.id, { status: next })
    u.status = next
    ElMessage.success(`已${next}：${u.name}`)
  } catch (e: any) {
    ElMessage.error('操作失败：' + (e?.message ?? '未知错误'))
  }
}

const remove = async (u: SysUserItem) => {
  try {
    await ElMessageBox.confirm(`确认删除账号「${u.name}」？此操作不可恢复。`, '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteUser(u.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

const resetPwd = async (u: SysUserItem) => {
  const pwd = '123456'
  try {
    await updateUser(u.id, { password: pwd })
    ElMessage.success(`已重置「${u.name}」密码为 ${pwd}`)
  } catch (e: any) {
    ElMessage.error('重置失败：' + (e?.message ?? '未知错误'))
  }
}
</script>

<template>
  <div class="team-wrap" v-loading="loading">
    <div class="team-head">
      <p class="team-tip">
        团队成员即系统登录账号，关联红娘业绩档案。角色决定权限：超级管理员 &gt; 运营主管 &gt; 红娘。
      </p>
      <button v-if="canWrite" class="btn btn-gold btn-sm" @click="openCreate">
        <app-icon name="user-plus" :size="14" /> 新建账号
      </button>
    </div>

    <div class="team-table glass-card">
      <div class="team-row team-row-head">
        <span>姓名</span><span>账号</span><span>角色</span><span>手机</span><span>状态</span><span class="col-act">操作</span>
      </div>
      <div v-for="u in list" :key="u.id" class="team-row">
        <span class="t-name">{{ u.name }}</span>
        <span class="t-uname">@{{ u.username }}</span>
        <span>
          <span class="role-tag" :class="'role-' + (u.role || '').toLowerCase()">{{ roleText(u.role) }}</span>
        </span>
        <span class="t-phone">{{ u.phone ? u.phone.slice(0, 3) + '****' + u.phone.slice(7) : '—' }}</span>
        <span>
          <el-tag :type="statusType(u.status)" size="small" effect="light">{{ u.status || '启用' }}</el-tag>
        </span>
        <span class="col-act t-actions">
          <button class="lnk" @click="openEdit(u)">编辑</button>
          <button class="lnk" @click="toggleStatus(u)">{{ u.status === '启用' ? '停用' : '启用' }}</button>
          <button class="lnk" @click="resetPwd(u)">重置密码</button>
          <button class="lnk danger" @click="remove(u)">删除</button>
        </span>
      </div>
      <div v-if="!list.length" class="team-empty">暂无团队成员</div>
    </div>

    <el-dialog v-model="dialog" :title="editingId ? '编辑账号' : '新建账号'" width="460px">
      <el-form :model="form" label-width="84px">
        <el-form-item label="登录账号">
          <el-input v-model="form.username" :disabled="!!editingId" placeholder="用于登录的用户名" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" placeholder="展示姓名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="超级管理员" value="ADMIN" />
            <el-option label="运营主管" value="MANAGER" />
            <el-option label="红娘" value="AGENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联红娘">
          <el-input v-model="form.agentId" placeholder="可填红娘档案编号（agentId）" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item :label="editingId ? '重置密码' : '初始密码'">
          <el-input v-model="form.password" :placeholder="editingId ? '留空则不修改' : '默认 123456'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">{{ editingId ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.team-wrap { display: flex; flex-direction: column; gap: 16px; }
.team-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.team-tip { margin: 0; font-size: 0.8125rem; color: var(--dt-ink-3); max-width: 70%; }
.team-table { padding: 8px 4px; border-radius: var(--dt-radius-lg); }
.team-row {
  display: grid;
  grid-template-columns: 1.2fr 1.4fr 1.1fr 1.3fr 0.8fr 2fr;
  align-items: center; gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--dt-border);
  font-size: 0.8438rem; color: var(--dt-ink-2);
}
.team-row:last-child { border-bottom: none; }
.team-row-head { font-weight: 600; color: var(--dt-ink-4); font-size: 0.75rem; }
.t-name { font-weight: 600; color: var(--dt-ink-1); }
.t-uname { color: var(--dt-ink-3); }
.t-phone { color: var(--dt-ink-3); }
.col-act { text-align: right; }
.t-actions { display: flex; gap: 10px; justify-content: flex-end; flex-wrap: wrap; }
.lnk {
  background: none; border: none; cursor: pointer;
  color: var(--dt-primary); font-size: 0.8125rem; padding: 0;
}
.lnk:hover { text-decoration: underline; }
.lnk.danger { color: var(--dt-error); }
.role-tag { padding: 2px 9px; border-radius: var(--dt-radius-sm); font-size: 0.75rem; font-weight: 600; }
.role-admin { background: var(--dt-primary-soft-2); color: var(--dt-primary); }
.role-manager { background: var(--dt-info-soft); color: var(--dt-info); }
.role-agent { background: var(--dt-success-soft); color: var(--dt-success); }
.team-empty { padding: 28px; text-align: center; color: var(--dt-ink-4); font-size: 0.8125rem; }

@media (max-width: 1100px) {
  .team-tip { max-width: 100%; }
  .team-row { grid-template-columns: 1fr 1fr; }
  .team-row-head { display: none; }
}
</style>
