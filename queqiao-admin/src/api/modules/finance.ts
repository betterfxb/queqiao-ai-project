import { api, download } from '../http'
import type { FinanceRecord } from '../types'

const unwrap = (r: any) => (Array.isArray(r) ? r : r?.list ?? [])

/** 财务交易记录 */
export const fetchFinance = () =>
  api<FinanceRecord[]>('/finance/records').then(unwrap)

/** 订单列表（含完整字段，用于审核/确认/退款） */
export const fetchOrders = () =>
  api('/finance/orders').then(unwrap)

export const fetchFinanceStats = () =>
  api('/finance/stats')

export const createOrder = (data: Record<string, unknown>) =>
  api('/finance/orders', { method: 'POST', body: data })

export const updateOrder = (id: string, data: Record<string, unknown>) =>
  api('/finance/orders/' + id, { method: 'PUT', body: data })

export const confirmOrder = (id: string) =>
  api('/finance/orders/' + id + '/confirm', { method: 'POST' })

export const refundOrder = (id: string, reason?: string) =>
  api('/finance/orders/' + id + '/refund', { method: 'POST', body: reason ? { reason } : {} })

export const deleteOrder = (id: string) =>
  api('/finance/orders/' + id, { method: 'DELETE' })

/** 导出 CSV：直接触发浏览器下载真实文件 */
export const exportFinance = () =>
  download('/finance/export', `财务流水_${new Date().toISOString().slice(0, 10)}.csv`)
