import { request } from './http'

/** 钱包账户视图（金额 = 币种最小单位 Long） */
export interface AccountItem {
  accountId?: number
  userId?: number
  coinId?: number
  symbol?: string
  /** 可用余额（最小单位） */
  available?: number
  /** 冻结余额（最小单位） */
  frozen?: number
  /** 总余额（最小单位） */
  total?: number
  /** 账户状态：0=禁用 1=正常 2=冻结 */
  status?: number
  /** 乐观锁版本 */
  version?: number
}

/** 查询用户资产总览：返回该用户全部币种账户 */
export function accounts(userId: number): Promise<AccountItem[]> {
  return request<AccountItem[]>({ url: '/asset/accounts', method: 'get', params: { userId } })
}

/** 查询单币种余额 */
export function balance(userId: number, symbol: string): Promise<AccountItem> {
  return request<AccountItem>({ url: '/asset/balance', method: 'get', params: { userId, symbol } })
}
