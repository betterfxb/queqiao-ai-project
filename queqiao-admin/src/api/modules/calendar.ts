import { api } from '../http'

export interface CalEvent {
  id: string
  title: string
  date?: string
  startTime?: string
  endTime?: string
  allDay?: boolean
  type?: string
  place?: string
  member?: string
  agent?: string
  note?: string
  status?: string
}

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

/** 日程事件：可按 year+month、from+to 或全量查询 */
export const fetchCalendar = (q?: { year?: number; month?: number; from?: string; to?: string }) =>
  api<CalEvent[]>('/calendar/events', {
    query: q as Record<string, string | undefined>,
  }).then(unwrap)

export const fetchCalendarToday = () =>
  api<CalEvent[]>('/calendar/today').then(unwrap)

export const fetchCalendarStats = (q?: { year?: number; month?: number }) =>
  api('/calendar/stats', { query: q as Record<string, string | undefined> })

export const createEvent = (data: Record<string, unknown>) =>
  api('/calendar/events', { method: 'POST', body: data })

export const updateEvent = (id: string, data: Record<string, unknown>) =>
  api('/calendar/events/' + id, { method: 'PUT', body: data })

export const deleteEvent = (id: string) =>
  api('/calendar/events/' + id, { method: 'DELETE' })

export const setEventStatus = (id: string, status: string) =>
  api('/calendar/events/' + id + '/status', { method: 'POST', body: { status } })
