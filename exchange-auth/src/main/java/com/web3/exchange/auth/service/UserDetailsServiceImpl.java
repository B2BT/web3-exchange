package com.web3.exchange.auth.service;

import com.web3.exchange.auth.feign.UserServiceClient;
import com.web3.exchange.auth.security.domain.AuthUser;
import com.web3.exchange.auth.security.domain.UserPrincipal;
import com.web3.exchange.common.user.UserDetailDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 用户详情服务：实现 Spring Security 的 UserDetailsService。
 * <p>
 * auth 服务不连数据库，用户信息通过 Feign 从 exchange-user 服务实时获取，
 * 并将 {@link UserDetailDTO} 转换为 Spring Security 可识别的 {@link UserPrincipal}。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserServiceClient userServiceClient;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            UserDetailDTO userDetail = userServiceClient.getUserInfo(username);
            if (userDetail == null) {
                throw new UsernameNotFoundException("用户不存在: " + username);
            }
            AuthUser authUser = AuthUser.fromDetailDTO(userDetail);
            return UserPrincipal.create(authUser, userDetail.getPermissions());
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("通过Feign加载用户[{}]失败", username, e);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
    }
}
