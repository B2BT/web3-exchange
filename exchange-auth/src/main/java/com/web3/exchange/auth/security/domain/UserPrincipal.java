package com.web3.exchange.auth.security.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring Securiy认证体系的适配器
 * 连接数据库与Spring Security核心框架的桥梁，
 * Spring Security并不直接操作业务User对象，而是通过UserDetails接口来读取用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 用户状态：0-正常，1-禁用
     */
    private Integer status;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 登录时间戳
     */
    private Long loginTime;

    /**
     * 过期时间戳
     */
    private Long expireTime;

    /**
     * 登录IP
     */
    private String loginIp;

    /**
     * 权限列表
     */
    private Set<String> permissions = new HashSet<>();

    /**
     * 角色列表
     */
    private Set<String> roles = new HashSet<>();

    /**
     * 附加属性
     */
    private transient Object attributes;

    /**
     * 获取权限列表
     * @return
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (CollectionUtils.isEmpty(permissions)) {
            return Collections.emptyList();
        }
        // 将一组字符串类型的权限标识（如sys:user:add）转换成Spring Security能够识别的权限对象（GrantedAuthority）
        // .map意思映射或转换：把流中的每个元素，按照指定规则转化成另一种东西
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    /**
     * 获取权限字符串列表
     */
    public List<String> getPermissionStrings() {
        return List.copyOf(permissions);
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    /**
     * 账号是否过期
     * @return
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账号是否锁定
     * @return
     */
    @Override
    public boolean isAccountNonLocked() {
        return !Integer.valueOf(1).equals(status);
    }

    /**
     * 凭证是否过期
     * @return
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账号是否启用
     * @return
     */
    @Override
    public boolean isEnabled() {
        return !Integer.valueOf(1).equals(status);
    }

    /**
     * 创建UserPrincipal
     */
    public static UserPrincipal create(AuthUser user, List<String> permissions) {
        return UserPrincipal.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .password(user.getEncryptedPassword())
                .status(user.getStatus())
                .permissions(new HashSet<>(permissions))
                .roles(user.getRoles() != null ? new HashSet<>(user.getRoles()) : new HashSet<>())
                .build();
    }

    /**
     * 创建带属性的UserPrincipal
     */
    public static UserPrincipal create(AuthUser user, List<String> permissions, Map<String, Object> attributes) {
        UserPrincipal userPrincipal = create(user, permissions);
        userPrincipal.setAttributes(attributes);
        return userPrincipal;
    }
}
