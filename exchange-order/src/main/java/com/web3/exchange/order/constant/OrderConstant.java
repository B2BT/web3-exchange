package com.web3.exchange.order.constant;

/**
 * 订单域常量（side / orderType / status 编码，与 docs/order-domain.md 一致）。
 */
public final class OrderConstant {

    private OrderConstant() {
    }

    /** 方向：买入 */
    public static final int SIDE_BUY = 1;
    /** 方向：卖出 */
    public static final int SIDE_SELL = 2;

    /** 类型：GTC 限价 */
    public static final int TYPE_LIMIT = 1;
    /** 类型：市价 */
    public static final int TYPE_MARKET = 2;

    /** 状态：NEW 待撮合（挂单中） */
    public static final int STATUS_NEW = 0;
    /** 状态：PARTIAL_FILLED 部分成交 */
    public static final int STATUS_PARTIAL = 1;
    /** 状态：FILLED 全部成交（终止态） */
    public static final int STATUS_FILLED = 2;
    /** 状态：CANCELLED 已撤单（终止态） */
    public static final int STATUS_CANCELLED = 3;
    /** 状态：REJECTED 已拒绝（终止态） */
    public static final int STATUS_REJECTED = 4;

    /** 资产业务类型：冻结（给 asset 用） */
    public static final String BIZ_FREEZE = "FREEZE";
    /** 资产业务类型：解冻（给 asset 用） */
    public static final String BIZ_UNFREEZE = "UNFREEZE";
    /** 资产业务类型：过户（给 asset 用） */
    public static final String BIZ_TRANSFER = "TRANSFER";
}
