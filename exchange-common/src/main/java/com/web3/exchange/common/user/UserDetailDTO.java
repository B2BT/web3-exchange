package com.web3.exchange.common.user;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.autoconfigure.security.SecurityProperties;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 用户详情DTO
 * 包含敏感信息的用户DTO
 * // @EqualsAndHashCode自动为你生成 equals(Object other) 和 hashCode() 这两个方法。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDTO extends UserDTO {
    private String password;
    private String salt;
    private Integer loginFailureCount;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    /** 是否开启2FA：0-未开启，1-已开启 */
    private Integer twoFactorEnabled;
    /** 2FA base32 密钥（RFC 6238），仅当 twoFactorEnabled==1 时有意义 */
    private String secretKey;
}
