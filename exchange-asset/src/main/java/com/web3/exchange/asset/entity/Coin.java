package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 币种表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_coin")
public class Coin extends BaseEntity {
    /** 币种符号:BTC/ETH/USDT */
    private String symbol;
    /** 币种名称 */
    private String name;
    /** 币种类型:COIN=原生币,TOKEN=代币 */
    private String coinType;
    /** 所属链编码(关联t_chain) */
    private String chainCode;
    /** 代币合约地址(原生币为空) */
    private String contractAddress;
    /** 精度(最小单位位数) */
    private Integer decimals;
    /** 是否允许提现:0=否,1=是 */
    private Integer withdrawEnabled;
    /** 是否允许充值:0=否,1=是 */
    private Integer depositEnabled;
    /** 提现固定手续费(最小单位) */
    private Long withdrawFee;
    /** 最小提现额(最小单位) */
    private Long minWithdraw;
    /** 单笔最大提现额(最小单位) */
    private Long maxWithdraw;
    /** 最小充值额(最小单位) */
    private Long minDeposit;
    /** 当日提现限额(最小单位) */
    private Long dailyWithdrawLimit;
    /** 状态:0=禁用,1=正常 */
    private Integer status;
    /** 排序 */
    private Integer sort;
}
