package com.web3.exchange.user.vo;

import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
public class UserVO {
    @Serial
    private static final long  serialVersionUID = 1L;

    private String id;
    private String username;
    private String password;
    private String email;
    private String phone;
    private String nickname;
    private String realname;
    private String avatar;
    private Integer status;
    private Integer accountNonExpired;
    private Integer accountNonLocked;
    private Integer credentialNonExpired;
    private Integer enabled;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime loginFailTime;
    private Integer loginFailCount;
    private LocalDateTime lockUntil;
    private LocalDateTime passwordUpdateTime;
    private LocalDateTime passwordExpireTime;
    private String secretKey;
    private Integer twoFactorEnabled;
    private String twofactorType;
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
    private LocalDateTime kycVerifyTime;
    private String walletAddress;
    private String walletType;
    private Integer walletVerified;
}
