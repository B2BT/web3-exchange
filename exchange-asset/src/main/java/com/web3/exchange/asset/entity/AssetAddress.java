package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 充币地址表（t_asset_address）——用户用于充值的链上收款地址。
 * <p>
 * 业务角色：为每个用户按链/币种生成专属充币地址（或复用热钱包派生地址），
 * chain 服务据此识别链上入账归属用户。同一链上地址只能归属一次
 * （唯一索引 uk_chain_address(chain_code, address)），防地址复用冲突。
 * </p>
 * <p>
 * 地址类型 address_type：1=用户充币地址，2=热钱包，3=冷钱包；
 * memo 记录 TRON 等链的地址 Tag；isActive 是否启用；lastUsedTime 最近使用时间。
 * 冷热钱包地址派生、热钱包轮换等能力将在 Phase 2 完善。
 * </p>
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
