import { api } from '../http'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

/** 团队成员（系统账号） */
export interface SysUserItem {
  id: string
  username: string
  name: string
  role: 'ADMIN' | 'MANAGER' | 'AGENT'
  phone?: string
  email?: string
  status?: string
  agentId?: string
  avatarBg?: string
  createdAt?: string
  lastLoginAt?: string
}

/** 团队管理：账号列表（主管及以上可读） */
export const fetchUsers = (role?: string) =>
  api<SysUserItem[]>('/users', { query: role ? { role } : undefined }).then(unwrap)

/** 新建账号（默认密码 123456） */
export const createUser = (data: Partial<SysUserItem> & { username: string; password?: string }) =>
  api<SysUserItem>('/users', { method: 'POST', body: data })

/** 更新账号（改角色 / 资料 / 启用停用 / 改密） */
export const updateUser = (id: string, data: Partial<SysUserItem> & { password?: string }) =>
  api<SysUserItem>('/users/' + id, { method: 'PUT', body: data })

/** 删除账号（禁止删除自己） */
export const deleteUser = (id: string) =>
  api<{ ok: boolean }>('/users/' + id, { method: 'DELETE' })
