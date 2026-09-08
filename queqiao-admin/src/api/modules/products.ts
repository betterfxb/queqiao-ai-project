import { api } from '../http'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

/** 服务产品（SKU） */
export interface ServiceProductItem {
  id: string
  name: string
  category: string
  price: number
  durationDays?: number
  benefits?: string
  status?: string
  createdAt?: string
}

export const PRODUCT_CATEGORIES = ['会员卡', '一对一套餐', '活动票', '增值包', '咨询课时']

/** 产品列表（主管及以上可读） */
export const fetchProducts = (params?: { category?: string; status?: string }) =>
  api<ServiceProductItem[]>('/products', { query: params }).then(unwrap)

/** 新建产品（管理员） */
export const createProduct = (data: Partial<ServiceProductItem> & { name: string; category: string; price: number }) =>
  api<ServiceProductItem>('/products', { method: 'POST', body: data })

/** 编辑产品（管理员） */
export const updateProduct = (id: string, data: Partial<ServiceProductItem>) =>
  api<ServiceProductItem>('/products/' + id, { method: 'PUT', body: data })

/** 删除 / 下架产品（管理员） */
export const deleteProduct = (id: string) =>
  api<{ ok: boolean }>('/products/' + id, { method: 'DELETE' })
