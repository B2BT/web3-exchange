package com.web3.exchange.asset.service;

/**
 * 资产业务类型常量（与 t_asset_ledger.biz_type 对应）
 */
public final class BizType {
    private BizType() {
    }

    /** 冻结 */
    public static final String FREEZE = "FREEZE";
    /** 解冻 */
    public static final String UNFREEZE = "UNFREEZE";
    /** 过户入 */
    public static final String TRANSFER_IN = "TRANSFER_IN";
    /** 过户出 */
    public static final String TRANSFER_OUT = "TRANSFER_OUT";
    /** 充值入账 */
    public static final String DEPOSIT = "DEPOSIT";
    /** 提现 */
    public static final String WITHDRAW = "WITHDRAW";
    /** 手续费 */
    public static final String FEE = "FEE";
    /** 返佣 */
    public static final String REBATE = "REBATE";
}
