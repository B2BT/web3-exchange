package com.web3.exchange.auth.security.domain;

import com.web3.exchange.common.user.UserDTO;
import com.web3.exchange.common.user.UserDetailDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashSet;
import java.util.Set;

/**
 * 认证服务本地缓存用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 加密后的密码
     */
    private String encryptedPassword;

    /**
     * 用户状态
     */
    private Integer status;

    /**
     * 权限列表
     */
    private Set<String> permissions = new HashSet<>();

    /**
     * 角色列表
     */
    private Set<String> roles = new HashSet<>();

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 最后更新时间
     */
    private Long lastUpdateTime;

    /**
     * 从UserDTO转换
     */
    public static AuthUser fromDTO(UserDTO userDTO) {
        return AuthUser.builder()
                .userId(userDTO.getId())
                .username(userDTO.getUsername())
                .status(userDTO.getStatus())
                .roles(new HashSet<>(userDTO.getRoles()))
                .permissions(new HashSet<>(userDTO.getPermissions()))
                .tenantId(userDTO.getTenantId())
                .lastUpdateTime(System.currentTimeMillis())
                .build();
    }

    /**
     * 从UserDetailDTO转换
     */
    public static AuthUser fromDetailDTO(UserDetailDTO userDetailDTO) {
        AuthUser authUser = fromDTO(userDetailDTO);
        authUser.setEncryptedPassword(userDetailDTO.getPassword());
        return authUser;
    }
}
