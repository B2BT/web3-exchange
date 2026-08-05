package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 链配置表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_chain")
public class Chain extends BaseEntity {
    /** 链编码:ETH/BSC/TRON/POLYGON */
    private String chainCode;
    /** 链名称 */
    private String chainName;
    /** 链类型:EVM/TRON/OTHER */
    private String chainType;
    /** 网络链ID(EIP-155,TRON为NULL) */
    private Long chainId;
    /** RPC节点地址 */
    private String rpcUrl;
    /** 浏览器地址 */
    private String explorerUrl;
    /** 原生币种(Gas币) */
    private String currency;
    /** 充值入账所需确认数 */
    private Integer confirmations;
    /** 提现成功确认数 */
    private Integer withdrawConfirmations;
    /** 是否开启区块扫描:0=否,1=是 */
    private Integer scanEnabled;
    /** 最小Gas单价(wei) */
    private Long minGasPrice;
    /** 最大Gas单价(wei) */
    private Long maxGasPrice;
    /** 状态:0=禁用,1=正常 */
    private Integer status;
    /** 排序 */
    private Integer sort;
}
