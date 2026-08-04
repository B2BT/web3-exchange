package com.web3.exchange.auth.service.Impl;

import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.LoginResponse;
import com.web3.exchange.auth.dto.response.TokenPair;
import com.web3.exchange.auth.dto.response.UserInfoResponse;
import com.web3.exchange.auth.security.domain.UserPrincipal;
import com.web3.exchange.auth.security.jwt.JwtTokenProvider;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.common.exception.AuthException;
import com.web3.exchange.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        try {
            // 1. 执行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 2. 设置安全上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 3. 获取用户信息
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

            // 4. 生成双令牌
            TokenPair tokenPair = jwtTokenProvider.generateTokenPair(authentication);

            // 5. 检查Access Token是否即将过期
            boolean needRefresh = jwtTokenProvider.isAccessTokenExpiringSoon(
                    tokenPair.getAccessToken()
            );
            tokenPair.setNeedRefresh(needRefresh);

            // 6. 根据认证主体构造用户信息响应
            UserInfoResponse userInfo = UserInfoResponse.simple(
                    userPrincipal.getUserId(),
                    userPrincipal.getUsername(),
                    userPrincipal.getUsername()
            );

            return LoginResponse.builder()
                    .accessToken(tokenPair.getAccessToken())
                    .refreshToken(tokenPair.getRefreshToken())
                    .tokenType(tokenPair.getTokenType())
                    .expiresIn(tokenPair.getAccessTokenExpiresIn())
                    .refreshExpiresIn(tokenPair.getRefreshTokenExpiresIn())
                    .userInfo(userInfo)
                    .build();

        } catch (Exception e) {
            log.error("登录失败", e);
            throw new AuthException("登录失败: " + e.getMessage());
        }
    }

    @Override
    public TokenPair refreshToken(String refreshToken) {
        try {
            // 使用Refresh Token刷新双令牌
            return jwtTokenProvider.refreshTokenPair(refreshToken);
        } catch (Exception e) {
            log.error("刷新令牌失败", e);
            throw new AuthException("刷新令牌失败: " + e.getMessage());
        }
    }

    @Override
    public String refreshAccessToken(String refreshToken) {
        try {
            // 仅刷新Access Token
            return jwtTokenProvider.refreshAccessToken(refreshToken);
        } catch (Exception e) {
            log.error("刷新Access Token失败", e);
            throw new AuthException("刷新Access Token失败: " + e.getMessage());
        }
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        try {
            if (accessToken != null) {
                // 将Access Token加入黑名单（剩余有效期内不可用）
                jwtTokenProvider.blacklistToken(accessToken, 3600); // 1小时
            }

            if (refreshToken != null) {
                // 将Refresh Token加入黑名单
                jwtTokenProvider.blacklistToken(refreshToken, 3600);
            }

            // 清除安全上下文
            SecurityContextHolder.clearContext();

        } catch (Exception e) {
            log.error("登出失败", e);
            throw new ServiceException("登出失败");
        }
    }

    @Override
    public void logoutAll(Long userId) {
        try {
            // 撤销用户的所有Refresh Token
            jwtTokenProvider.revokeAllRefreshTokens(userId);

            // 清除安全上下文
            SecurityContextHolder.clearContext();

        } catch (Exception e) {
            log.error("强制登出失败", e);
            throw new ServiceException("强制登出失败");
        }
    }

    @Override
    public boolean validateToken(String accessToken) {
        try {
            return jwtTokenProvider.validateAccessToken(accessToken);
        } catch (Exception e) {
            log.error("验证令牌失败", e);
            return false;
        }
    }
}
