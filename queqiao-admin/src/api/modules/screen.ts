import { api } from '../http'

/** 数据大屏聚合（含满意度闭环指标：feedbackAvg / lowSatisfactionCount） */
export const fetchScreen = () => api<any>('/screen')
