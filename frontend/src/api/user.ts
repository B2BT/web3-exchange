import { request } from './http'

/** 用户详情（GET /api/users/info/{username} 返回，脱敏展示层不取敏感字段） */
export interface UserProfile {
  id?: number
  username?: string
  nickname?: string
  realName?: string
  email?: string
  phone?: string
  avatar?: string
  status?: number
  /** 0=未开启 1=已开启 */
  twoFactorEnabled?: number
  userLevel?: string
  roles?: string[]
  createTime?: string
  lastLoginTime?: string
}

/** KYC 认证状态 */
export interface KycStatus {
  /** 0=未认证 1=审核中 2=已认证 3=拒绝 */
  kycStatus?: number
  /** 0=未认证 1=L1 2=L2 3=L3 */
  kycLevel?: number
}

/** 查询用户资料 */
export function getUserInfo(username: string): Promise<UserProfile> {
  return request<UserProfile>({ url: `/users/info/${username}`, method: 'get' })
}

/** 查询 KYC 认证状态 */
export function getKycStatus(id: number): Promise<KycStatus> {
  return request<KycStatus>({ url: `/users/${id}/kyc/status`, method: 'get' })
}

/** 查询用户等级（NORMAL/VIP/SVIP） */
export function getUserLevel(id: number): Promise<string> {
  return request<string>({ url: `/users/${id}/level`, method: 'get' })
}
