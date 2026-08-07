import { request } from './http'

/** MyBatis-Plus 分页返回结构 */
export interface PageResult<T> {
  records?: T[]
  total?: number
  size?: number
  current?: number
  pages?: number
}

/** 充币地址视图 */
export interface AssetAddress {
  id?: number
  userId?: string | number
  chainCode?: string
  symbol?: string
  address?: string
  memo?: string
  addressType?: number
  isActive?: number
}

/** 充值记录视图（金额 = 最小单位 Long） */
export interface DepositItem {
  id?: number
  userId?: string | number
  symbol?: string
  chainCode?: string
  fromAddress?: string
  toAddress?: string
  amount?: number
  fee?: number
  txHash?: string
  blockHeight?: number
  confirmations?: number
  requiredConfirmations?: number
  ledgerId?: number
  /** 0=监听中 1=待确认 2=已入账 3=失败 */
  status?: number
  remark?: string
  createTime?: string
}

/** 提现记录视图（金额 = 最小单位 Long） */
export interface WithdrawItem {
  id?: number
  requestId?: string
  userId?: string | number
  symbol?: string
  chainCode?: string
  toAddress?: string
  amount?: number
  fee?: number
  realAmount?: number
  /** 0=待审核 1=审核中 2=处理中 3=成功 4=拒绝 5=失败回滚 */
  status?: number
  auditBy?: string
  auditTime?: string
  auditRemark?: string
  freezeLedgerId?: number
  txHash?: string
  failReason?: string
  createTime?: string
}

/** 提现申请请求（金额 = 币种最小单位 Long） */
export interface WithdrawApplyRequest {
  userId: string | number
  symbol: string
  chainCode: string
  toAddress: string
  amount: number
}

/** 查询用户充币地址（后端无则自动生成 BIP44 派生地址） */
export function depositAddress(
  userId: string | number,
  chainCode: string,
  symbol: string,
): Promise<AssetAddress> {
  return request<AssetAddress>({
    url: '/chain/deposit/address',
    method: 'get',
    params: { userId, chainCode, symbol },
  })
}

/** 充值记录分页 */
export function depositList(userId: string | number, page = 1, size = 20): Promise<PageResult<DepositItem>> {
  return request<PageResult<DepositItem>>({
    url: '/chain/deposit/list',
    method: 'get',
    params: { userId, page, size },
  })
}

/** 申请提现 */
export function withdrawApply(data: WithdrawApplyRequest): Promise<WithdrawItem> {
  return request<WithdrawItem>({ url: '/chain/withdraw/apply', method: 'post', data })
}

/** 提现记录分页 */
export function withdrawList(userId: string | number, page = 1, size = 20): Promise<PageResult<WithdrawItem>> {
  return request<PageResult<WithdrawItem>>({
    url: '/chain/withdraw/list',
    method: 'get',
    params: { userId, page, size },
  })
}

// ================= 自托管钱包 (M2) =================

/** 自托管钱包视图 */
export interface WalletItem {
  id?: number
  userId?: string | number
  chainCode?: string
  /** HD=助记词派生 / PRIVATE=导入私钥 / READONLY=只读 */
  walletType?: string
  address?: string
  addressType?: string
  name?: string
  /** 仅创建时返回一次 */
  mnemonic?: string
  status?: number
  createTime?: string
}

/** 链上余额（最小单位 Long） */
export interface WalletBalance {
  chainCode?: string
  symbol?: string
  coinType?: string
  balance?: string | number
  decimals?: number
}

/** 创建自托管钱包 */
export function walletCreate(data: { userId: string | number; chainCode: string; name?: string }): Promise<WalletItem> {
  return request<WalletItem>({ url: '/chain/wallet/create', method: 'post', data })
}

/** 导入自托管钱包（助记词或私钥二选一） */
export function walletImport(data: {
  userId: string | number
  chainCode: string
  mnemonic?: string
  privateKey?: string
  name?: string
}): Promise<WalletItem> {
  return request<WalletItem>({ url: '/chain/wallet/import', method: 'post', data })
}

/** 用户钱包列表 */
export function walletList(userId: string | number): Promise<WalletItem[]> {
  return request<WalletItem[]>({ url: '/chain/wallet/list', method: 'get', params: { userId } })
}

/** 钱包链上余额 */
export function walletBalance(userId: string | number, walletId: string | number): Promise<WalletBalance[]> {
  return request<WalletBalance[]>({
    url: `/chain/wallet/${walletId}/balance`,
    method: 'get',
    params: { userId },
  })
}
