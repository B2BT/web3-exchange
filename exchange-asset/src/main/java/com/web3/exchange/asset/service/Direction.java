package com.web3.exchange.asset.service;

/**
 * 资金方向常量（与 t_asset_ledger.direction 对应）
 */
public final class Direction {
    private Direction() {
    }

    /** 流入：可用增加 */
    public static final int IN = 1;
    /** 流出：可用减少 */
    public static final int OUT = 2;
    /** 冻结：可用减少，冻结增加 */
    public static final int FROZEN = 3;
    /** 解冻：冻结减少，可用增加 */
    public static final int UNFROZEN = 4;
    /** 冻结扣减（过户转出）：仅冻结减少，可用不变 */
    public static final int FROZEN_OUT = 5;
}
