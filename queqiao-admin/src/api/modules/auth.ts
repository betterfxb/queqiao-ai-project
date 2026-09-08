import { api } from '../http'

export interface CurrentUser {
  id: string
  username: string
  name: string
  role: string
  avatar?: string
  avatarBg?: string
  phone?: string
  email?: string
  agentId?: string
  lastLoginAt?: string
}

/** 当前登录用户 */
export const fetchMe = () => api<CurrentUser>('/auth/me')

/** 更新个人资料 */
export const updateProfile = (data: Partial<Pick<CurrentUser, 'name' | 'phone' | 'email'>>) =>
  api<CurrentUser>('/auth/profile', { method: 'PUT', body: data })

/** 修改登录密码 */
export const changePassword = (oldPassword: string, newPassword: string) =>
  api<{ ok: boolean; message: string }>('/auth/change-password', {
    method: 'POST',
    body: { oldPassword, newPassword },
  })

/** 登出埋点 */
export const logoutApi = () => api<{ ok: boolean }>('/auth/logout', { method: 'POST' })
