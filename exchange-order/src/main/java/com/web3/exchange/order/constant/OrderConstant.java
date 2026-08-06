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

    /** 时间策略：GTC 长期有效（默认） */
    public static final int TIF_GTC = 0;
    /** 时间策略：IOC 立即成交或取消剩余 */
    public static final int TIF_IOC = 1;
    /** 时间策略：FOK 全部成交否则取消 */
    public static final int TIF_FOK = 2;
    /** 时间策略：PostOnly 只挂单不吃单（仅限价） */
    public static final int TIF_POST_ONLY = 3;

    /** 条件单类型：非条件单 */
    public static final int TRIGGER_TYPE_NONE = 0;
    /** 条件单类型：止盈（最新价 >= 触发价激活） */
    public static final int TRIGGER_TYPE_TAKE_PROFIT = 1;
    /** 条件单类型：止损（最新价 <= 触发价激活） */
    public static final int TRIGGER_TYPE_STOP_LOSS = 2;

    /** 触发状态：待触发 */
    public static final int TRIGGER_STATUS_PENDING = 0;
    /** 触发状态：已触发（激活为普通单） */
    public static final int TRIGGER_STATUS_TRIGGERED = 1;
    /** 触发状态：已取消 */
    public static final int TRIGGER_STATUS_CANCELLED = 2;

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
