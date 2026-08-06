package com.web3.exchange.chain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 区块链配置表（t_chain）——平台接入的底层链参数配置（读 asset 库既有表，不建新表）。
 * <p>充值区块扫描、提现上链均依赖本表配置驱动：confirmations 充值入账确认数、
 * withdrawConfirmations 提现成功确认数、scanEnabled 扫描开关、min/maxGasPrice Gas 上下限。</p>
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
