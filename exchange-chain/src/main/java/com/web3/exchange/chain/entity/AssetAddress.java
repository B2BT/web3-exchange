package com.web3.exchange.chain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 充币地址表（t_asset_address）——用户用于充值的链上收款地址（读 asset 库既有表，不建新表）。
 * <p>chain 据此识别链上入账归属用户。本期由运营预配置 address_type=1 的用户充币地址；
 * 同一链上地址唯一（uk_chain_address(chain_code, address)）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_asset_address")
public class AssetAddress extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 链编码 */
    private String chainCode;
    /** 币种ID */
    private Long coinId;
    /** 币种符号 */
    private String symbol;
    /** 充币地址 */
    private String address;
    /** 备注/Tag(如TRON地址标签) */
    private String memo;
    /** 地址类型:1=用户充币地址,2=热钱包,3=冷钱包 */
    private Integer addressType;
    /** 是否启用:0=否,1=是 */
    private Integer isActive;
    /** 最近使用时间 */
    private LocalDateTime lastUsedTime;
}
