package com.web3.exchange.user.entity;

import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author yongzx
 */
@Data
public class User extends BaseEntity {
    private String username;
    private String password;
    private String email;
    private String phone;
    private String nickname;
    private String realName;
    private String avatar;
    private Integer status;
    private Integer accountNonExpired;
    private Integer accountNonLocked;
    private Integer credentialsNonExpired;
    private Integer enabled;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private Integer loginFailCount;
    private LocalDateTime lockUntil;
    private LocalDateTime passwordUpdateTime;
    private LocalDateTime passwordExpireTime;
    private String secretKey;
    private Integer twoFactorEnabled;
    private String twoFactorType;
    private String userLevel;
    private String inviteCode;
    private String invitedBy;
    private String registerSource;
    private String registerIp;
    private Integer kycStatus;
    private Integer kycLevel;
    private String idCardType;
    private String idCardNo;
    private String idCardFront;
    private String idCardBack;
    private LocalDateTime kycVerifyTime;
    private String walletAddress;
    private String walletType;
    private Integer walletVerified;
}
