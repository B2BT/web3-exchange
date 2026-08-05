package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 区块链配置表（t_chain）——平台接入的底层链参数配置。
 * <p>
 * 业务角色：集中管理每条链（ETH/BSC/TRON/POLYGON…）的节点、浏览器、确认数与 Gas
 * 参数，供充值区块扫描、提现上链等后续能力读取。chain_code 全局唯一（uk_chain_code），
 * 币种通过 chain_code 关联到本表。
 * </p>
 * <p>
 * 关键字段业务含义：confirmations 为充值入账所需区块确认数（链上到账需累计确认数达标
 * 才允许入账，防交易回滚）；withdrawConfirmations 为提现成功确认数；currency 为该链
 * 原生 Gas 币（如 ETH 的 ETH、TRON 的 TRX）；minGasPrice/maxGasPrice 为 Gas 单价上下限(wei)；
 * scanEnabled 是否开启区块扫描。
 * </p>
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
