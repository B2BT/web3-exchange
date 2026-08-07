package com.web3.exchange.chain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 用户自托管钱包表（t_user_wallet）——用户创建/导入的 Web3 钱包。
 * <p>walletType：HD=助记词派生（仅 HD 存 mnemonicEnc），PRIVATE=导入私钥，READONLY=只读绑定；
 * 私钥/助记词以 AES-GCM 加密后入库（secret 来自配置，生产用 KMS），address 为明文链上地址。
 * addressType：SELF=自托管（用户持钥），CUSTODIAL=托管（交易所持钥，M1 充币地址）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_user_wallet")
public class UserWallet extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 链编码:ETH/BSC/BTC/TRON */
    private String chainCode;
    /** 钱包类型:HD=助记词派生,PRIVATE=导入私钥,READONLY=只读绑定 */
    private String walletType;
    /** 加密助记词(AES-GCM,仅HD) */
    private String mnemonicEnc;
    /** 加密私钥(AES-GCM) */
    private String privateKeyEnc;
    /** 钱包地址 */
    private String address;
    /** 地址类型:SELF=自托管,CUSTODIAL=托管 */
    private String addressType;
    /** 钱包备注名 */
    private String name;
    /** 状态:0=禁用,1=正常 */
    private Integer status;
}
