import { request } from './http'

/** API 密钥 */
export interface ApiKeyItem {
  id?: string | number
  apiKey?: string
  secretKey?: string
  label?: string
  permission?: string
  status?: number
  lastUsedAt?: string
  createTime?: string
}

/** 创建API密钥 */
export function createApiKey(data: { label?: string; permission: string }): Promise<ApiKeyItem> {
  return request<ApiKeyItem>({ url: '/users/api-keys/create', method: 'post', data })
}
/** API密钥列表 */
export function listApiKeys(): Promise<ApiKeyItem[]> {
  return request<ApiKeyItem[]>({ url: '/users/api-keys/list', method: 'get' })
}
/** 停用/启用 */
export function toggleApiKey(id: string | number, enable: boolean): Promise<void> {
  return request<void>({ url: `/users/api-keys/${id}/toggle`, method: 'post', params: { enable } })
}
/** 删除 */
export function deleteApiKey(id: string | number): Promise<void> {
  return request<void>({ url: `/users/api-keys/${id}/delete`, method: 'post' })
}
