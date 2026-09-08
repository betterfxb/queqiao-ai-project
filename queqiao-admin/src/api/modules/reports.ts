import { api, download } from '../http'
import type { ReportRecord } from '../types'

const unwrap = (r: any) => (Array.isArray(r) ? r : r?.list ?? [])

/** 报表导出记录 */
export const fetchReports = (category?: string) =>
  api<ReportRecord[]>('/reports/records', {
    query: category ? { category } : undefined,
  }).then(unwrap)

export const fetchReportSummary = () =>
  api('/reports/summary')

export const generateReport = (data: Record<string, unknown>) =>
  api('/reports/generate', { method: 'POST', body: data })

/** 下载报表 CSV：直接触发浏览器下载真实文件 */
export const downloadReport = (id: string, name?: string) =>
  download('/reports/' + id + '/download', `${name || id}.csv`)

export const deleteReport = (id: string) =>
  api('/reports/' + id, { method: 'DELETE' })
