/**
 * 认证状态（Pinia）
 * - token 存于 sessionStorage（页面关闭即失效，降低 XSS 盗用后的存活窗口）
 * - 生产环境建议改为 HttpOnly + Secure + SameSite Cookie，由后端 Set-Cookie 下发，
 *   前端只持有短期可用的访问令牌（短 TTL + 刷新令牌轮换）。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { logoutApi } from '@/api/modules/auth'

const TOKEN_KEY = 'qq_token'
const USER_KEY = 'qq_user'
const ROLE_KEY = 'qq_role'

function safeGet(k: string): string {
  try {
    return sessionStorage.getItem(k) ?? ''
  } catch {
    return ''
  }
}
function safeSet(k: string, v: string) {
  try {
    sessionStorage.setItem(k, v)
  } catch {
    /* ignore */
  }
}
function safeDel(k: string) {
  try {
    sessionStorage.removeItem(k)
  } catch {
    /* ignore */
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(safeGet(TOKEN_KEY))
  const user = ref<string>(safeGet(USER_KEY))
  const role = ref<string>(safeGet(ROLE_KEY))
  const isAuthenticated = ref<boolean>(!!safeGet(TOKEN_KEY))

  function setSession(tk: string, name: string, r?: string) {
    token.value = tk
    user.value = name
    if (r) role.value = r
    isAuthenticated.value = true
    safeSet(TOKEN_KEY, tk)
    safeSet(USER_KEY, name)
    if (r) safeSet(ROLE_KEY, r)
  }

  function login(name: string, tk?: string, r?: string) {
    // 原型阶段：未接真实后端时用本地生成的短期令牌
    const fallback = `tk_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
    setSession(tk ?? fallback, name, r)
  }

  const isAdmin = computed(() => role.value === 'ADMIN')

  async function logout() {
    // 先通知后端失效当前会话令牌，再清理本地存储（后端失败也不影响本地登出）
    try {
      await logoutApi()
    } catch {
      /* 后端登出接口不可用时忽略，继续清理本地会话 */
    }
    token.value = ''
    user.value = ''
    role.value = ''
    isAuthenticated.value = false
    safeDel(TOKEN_KEY)
    safeDel(USER_KEY)
    safeDel(ROLE_KEY)
  }

  return { token, user, role, isAuthenticated, isAdmin, login, logout }
})
