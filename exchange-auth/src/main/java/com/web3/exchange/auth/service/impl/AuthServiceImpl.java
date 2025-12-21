package com.web3.exchange.auth.service.impl;

import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.AuthResponse;
import com.web3.exchange.auth.dto.response.TokenResponse;
import com.web3.exchange.auth.dto.response.UserInfoResponse;
import com.web3.exchange.auth.feign.UserServiceClient;
import com.web3.exchange.auth.security.jwt.JwtTokenProvider;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserServiceClient userServiceClient;


    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            // 封装用户请求和密码
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            // 设置Spring Security上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 生成token
            String username = authentication.getName();
            List<String> authrities = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            String accessToken = jwtTokenProvider.generateAccessToken(username, authentication.getAuthorities());
            String refreshToken = jwtTokenProvider.generateRefreshToken(username);

            // 存储Token到Redis
            tokenService.storeToken(username, accessToken, refreshToken);

            // 获取用户信息
            UserInfoResponse userInfo = userServiceClient.getUserInfo(username);

            // 记录到登录日志
            logLoginInfo(username, request.getLoginIp());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .userInfo(userInfo)
                    .authorities(authrities)
                    .build();
        } catch (AuthenticationException e) {
            log.error("登录失败：{}", e.getMessage(), e);
            throw new RuntimeException("登录失败：" + e.getMessage());
        }
    }


    /**
     * 使用RefreshToken生成AccessToken
     *
     * @param refreshToken
     * @return
     */
    @Override
    public TokenResponse refreshToken(String refreshToken) {
        // 验证Refresh Token
        if (!jwtTokenProvider.validateToken(refreshToken) ||
                !"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new AuthenticationServiceException("无效的Refresh Token");
        }
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // 验证redis的Refresh Token
        String storeRefreshToken = tokenService.getRefreshToken(username);
        if (!storeRefreshToken.equals(refreshToken)) {
            throw new AuthenticationServiceException("Refresh Token不匹配");
        }

        // 获取用户权限
        List<String> userAuthorities = userServiceClient.getUserAuthorities(username);

        // 生成新的AccessToken
        String newAccessToken = jwtTokenProvider.generateAccessToken(username,
                userAuthorities.stream()
                        .map(auth -> (GrantedAuthority) () -> auth)
                        .toList());

        // 更新token
        tokenService.updateAccessToken(username, newAccessToken);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();
    }

    @Override
    public void logout(String accessToken) {
        String username = jwtTokenProvider.getUsernameFromToken(accessToken);

        // 1.将Access Token加入黑名单，因为登出token还合法，所以要加入黑名单中
        tokenService.balcklistToken(accessToken);

        // 2.删除Refresh Token
        tokenService.delelteRefreshToken(username);

        // 3.清除用户会话缓存
        clearUserContext(username);
    }


    @Override
    public boolean validateToken(String token) {
        // 检查是否在黑名单中
        if(tokenService.isTokenBlacklisted(token)){
            return false;
        }
        return jwtTokenProvider.validateToken(token);
    }

    /**
     * 获取当前用户
     * @param accessToken
     * @return
     */
    @Override
    public UserInfoResponse getUserFromToken(String accessToken) {
        if(validateToken(accessToken)){

        }
        return null;
    }


    /**
     * 异步记录登录日志
     * @param username
     * @param loginIp
     */
    private void logLoginInfo(String username, String loginIp) {
        new Thread(()->{
            try {
                userServiceClient.updateLoginInfo(username, loginIp);
            } catch (Exception e) {
                log.error("记录登录日志失败",e);
            }
        });
    }

    private void clearUserContext(String username) {
        // 清理用户相关的缓存
        String userKey = "user:context:"+ username;
        redisTemplate.delete(userKey);
    }

}
