<script setup lang="ts">
/** 登录页 — 全屏独立页：左侧品牌装饰面板 + 右侧玻璃登录表单 */
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { api } from '@/api/http'
import LoginHero from './components/LoginHero.vue'
import LoginForm from './components/LoginForm.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)

interface LoginResp {
  token: string
  expiresIn: number
  user?: { id?: string; name?: string; role?: string; username?: string }
}

/** 表单校验 + 人机验证 + 失败限流均在 LoginForm 内完成，此处负责调用真实后端登录 */
const onSubmit = async (payload: { account: string; password: string }) => {
  loading.value = true
  try {
    const resp = await api<LoginResp>('/auth/login', {
      method: 'POST',
      body: { username: payload.account, password: payload.password },
    })
    const name = resp.user?.name || resp.user?.username || payload.account
    auth.login(name, resp.token, resp.user?.role)
    ElMessage.success('登录成功，欢迎回来')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败，请稍后再试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌面板 -->
    <LoginHero />

    <!-- 右侧登录表单 -->
    <div class="form-panel">
      <LoginForm :loading="loading" @submit="onSubmit" />
    </div>
  </div>
</template>

<style scoped>
.login-page { display: flex; height: 100vh; overflow: hidden; }

.form-panel {
  width: 45%; min-width: 480px; display: flex; align-items: center; justify-content: center;
  background: var(--dt-bg-base); padding: 40px;
}

/* 窄屏：收起品牌面板，表单居中占满 */
@media (max-width: 900px) {
  .login-page > :first-child { display: none; }
  .form-panel { width: 100%; min-width: 0; padding: 24px; }
}
</style>
