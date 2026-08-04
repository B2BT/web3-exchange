package com.web3.exchange.user.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户列表展示 VO（脱敏）
 * 仅暴露非敏感展示字段，不包含 password / secretKey / idCardNo / walletAddress 等敏感信息。
 */
@Data
public class UserVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private Integer twoFactorEnabled;
    private String userLevel;
    private String registerSource;
    private String inviteCode;
    private Integer kycStatus;
    private Integer kycLevel;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
