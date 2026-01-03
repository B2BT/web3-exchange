package com.web3.exchange.auth.service.Impl;

import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.AuthResponse;
import com.web3.exchange.auth.dto.response.TokenResponse;
import com.web3.exchange.auth.dto.response.UserInfoResponse;
import com.web3.exchange.auth.feign.UserServiceClient;
import com.web3.exchange.auth.security.jwt.JwtTokenProvider;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.auth.service.TokenService;
import com.web3.exchange.common.user.UserDetailDTO;
import jakarta.security.auth.message.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;
    private final UserServiceClient userServiceClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResponse login(LoginRequest request) {
        // 1.调用用户服务验证用户
        UserDetailDTO userInfo = userServiceClient.getUserInfo(request.getUsername());
        if (userInfo == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), userInfo.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        // 3. 验证用户状态
        if (userInfo.getStatus() != 0) {
            throw new RuntimeException("用户已被禁用");
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // 2. 设置认证上下文
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. 生成Token
        String username = authentication.getName();
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String accessToken = jwtTokenProvider.generateAccessToken(username, authentication.getAuthorities());
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        // 4. 存储Token到Redis
        tokenService.storeToken(username, accessToken, refreshToken);

        // 5. 获取用户信息
        UserDetailDTO userDetailDTO = userServiceClient.getUserInfo(username);

        // 6. 记录登录日志
        logLoginInfo(username, request.getLoginIp());

        UserInfoResponse from = UserInfoResponse.from(userInfo);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userInfo(from)
                .authorities(authorities)
                .build();
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        // 验证 Refresh Token
        if (!jwtTokenProvider.validateToken(refreshToken) ||
                !"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new RuntimeException("无效的Refresh Token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // 验证 Redis 中的 Refresh Token
        String storedRefreshToken = tokenService.getRefreshToken(username);
        if (!refreshToken.equals(storedRefreshToken)) {
            throw new RuntimeException("Refresh Token 不匹配");
        }

        // 获取用户权限
        List<String> authorities = userServiceClient.getUserAuthorities(username);

        // 生成新的 Access Token
        String newAccessToken = jwtTokenProvider.generateAccessToken(username,
                authorities.stream()
                        .map(auth -> (GrantedAuthority) () -> auth)
                        .toList());

        // 更新 Token
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

        // 1. 将 Access Token 加入黑名单
        tokenService.blacklistToken(accessToken);

        // 2. 删除 Refresh Token
        tokenService.deleteRefreshToken(username);

        // 3. 清除用户会话
        clearUserContext(username);
    }

    @Override
    public boolean validateToken(String token) {
        // 检查是否在黑名单中
        if (tokenService.isTokenBlacklisted(token)) {
            return false;
        }
        return jwtTokenProvider.validateToken(token);
    }

    @Override
    public UserInfoResponse getCurrentUserInfo() {
        return null;
    }

    private void logLoginInfo(String username, String loginIp) {
        // 异步记录登录日志
        new Thread(() -> {
            try {
                userServiceClient.updateLoginInfo(username, loginIp);
            } catch (Exception e) {
                log.error("记录登录日志失败", e);
            }
        }).start();
    }

    private void clearUserContext(String username) {
        // 清除用户相关的缓存
        String userKey = "user:context:" + username;
        redisTemplate.delete(userKey);
    }
}