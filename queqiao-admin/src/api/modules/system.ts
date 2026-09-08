import { api } from '../http'

/** 单个设置项（后端 SettingItem） */
export interface SettingItem {
  id: string
  category: string
  label: string
  value: string
  type: string
  updatedAt?: string
}

/** 后端按分类分组返回：{ '基础信息': SettingItem[], '业务规则': [...] } */
export type SettingsGrouped = Record<string, SettingItem[]>

export const fetchSettings = () => api<SettingsGrouped>('/settings')

/** 批量保存：body 为扁平的 { 'org.name': '值', ... } */
export const updateSettings = (data: Record<string, string>) =>
  api<{ ok: boolean; count: number }>('/settings', { method: 'PUT', body: data })

export const updateSetting = (key: string, value: string) =>
  api<SettingItem>('/settings/' + encodeURIComponent(key), { method: 'PUT', body: { value } })

export interface OperationLog {
  id?: string
  action?: string
  target?: string
  detail?: string
  operator?: string
  createdAt?: string
  [k: string]: unknown
}

export const fetchLogs = (target?: string) =>
  api<OperationLog[]>('/logs', { query: { target } }).then((r: any) =>
    Array.isArray(r) ? r : (r?.list ?? []),
  )

/** 服务端 AI 代理通道（带鉴权，转发到 Python :8090） */
export const aiAction = (action: string, data: Record<string, unknown>) =>
  api<Record<string, unknown>>('/ai/' + action, { method: 'POST', body: data })

/** 健康检查：后端 + AI 服务状态（真实探活，不再带 mock 降级） */
export const fetchHealth = () =>
  api<{ status: string; service: string; time: string; ai: string }>('/health')
