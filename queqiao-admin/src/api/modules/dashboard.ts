import { api } from '../http'

/** 工作台看板（后端已联通，聚合返回 kpis / recentMembers / kanban / todos / recs） */
export const fetchDashboard = () => api<any>('/dashboard')
