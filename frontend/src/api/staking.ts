import { request } from './http'
import type { PageResult } from './chain'

/** 质押产品 */
export interface StakingProduct {
  id?: string | number
  productCode?: string
  name?: string
  /** 0=活期 1=锁仓 */
  type?: number
  symbol?: string
  /** 年化利率(基点,10000=100%) */
  annualRateBp?: number
  minAmount?: number
  lockDays?: number
  status?: number
}

/** 质押持仓 */
export interface StakingPosition {
  id?: string | number
  userId?: string | number
  productCode?: string
  symbol?: string
  amount?: number
  accruedInterest?: number
  totalInterest?: number
  /** 0=质押中 1=已赎回 */
  status?: number
  startTime?: string
  lockEndTime?: string | null
  redeemTime?: string
}

/** 收益流水 */
export interface StakingInterest {
  id?: string | number
  userId?: string | number
  positionId?: string | number
  symbol?: string
  amount?: number
  settleDate?: string
}

/** 产品列表 */
export function stakingProducts(): Promise<StakingProduct[]> {
  return request<StakingProduct[]>({ url: '/staking/products', method: 'get' })
}

/** 质押 */
export function stakingStake(userId: string | number, productCode: string, amount: number): Promise<StakingPosition> {
  return request<StakingPosition>({
    url: '/staking/stake',
    method: 'post',
    data: { userId, productCode, amount },
  })
}

/** 赎回 */
export function stakingRedeem(userId: string | number, productCode: string): Promise<StakingPosition> {
  return request<StakingPosition>({
    url: '/staking/redeem',
    method: 'post',
    data: { userId, productCode, amount: 0 },
  })
}

/** 我的持仓 */
export function stakingPositions(userId: string | number): Promise<StakingPosition[]> {
  return request<StakingPosition[]>({ url: '/staking/positions', method: 'get', params: { userId } })
}

/** 收益流水分页 */
export function stakingInterests(userId: string | number, page = 1, size = 20): Promise<PageResult<StakingInterest>> {
  return request<PageResult<StakingInterest>>({
    url: '/staking/interests',
    method: 'get',
    params: { userId, page, size },
  })
}
