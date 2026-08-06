import { request } from './http'

/** 钱包账户视图（金额 = 币种最小单位 Long） */
export interface AccountItem {
  accountId?: number
  userId?: string | number
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
export function accounts(userId: string | number): Promise<AccountItem[]> {
  return request<AccountItem[]>({ url: '/asset/accounts', method: 'get', params: { userId } })
}

/** 查询单币种余额 */
export function balance(userId: string | number, symbol: string): Promise<AccountItem> {
  return request<AccountItem>({ url: '/asset/balance', method: 'get', params: { userId, symbol } })
}

/** 资金流水视图 */
export interface LedgerItem {
  id?: string
  requestId?: string
  userId?: string | number
  accountId?: string | number
  symbol?: string
  /** 业务类型：FREEZE/UNFREEZE/TRANSFER_IN/TRANSFER_OUT/DEPOSIT/WITHDRAW/FEE/REBATE */
  bizType?: string
  /** 方向：1=入 2=出 3=冻结 4=解冻 5=冻结转出 */
  direction?: number
  /** 金额（币种最小单位 Long） */
  amount?: number
  beforeAvailable?: number
  afterAvailable?: number
  beforeFrozen?: number
  afterFrozen?: number
  refNo?: string
  status?: number
  remark?: string
  createTime?: string
}

/** 分页结果 */
export interface LedgerPage {
  total: number
  current: number
  size: number
  pages: number
  records: LedgerItem[]
}

/** 查询用户资金流水（分页，createTime 降序） */
export function ledgerList(params: {
  userId: string | number
  page?: number
  size?: number
}): Promise<LedgerPage> {
  return request<LedgerPage>({ url: '/asset/ledger/list', method: 'get', params })
}
