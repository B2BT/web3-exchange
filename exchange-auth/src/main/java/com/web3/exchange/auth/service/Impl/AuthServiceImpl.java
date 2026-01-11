package com.web3.exchange.auth.service.Impl;

import com.web3.exchange.auth.domain.UserPrincipal;
import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.LoginResponse;
import com.web3.exchange.auth.dto.response.TokenPair;
import com.web3.exchange.auth.dto.response.TokenResponse;
import com.web3.exchange.auth.dto.response.UserInfoResponse;
import com.web3.exchange.auth.security.jwt.JwtTokenProvider;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.auth.service.CaptchaService;
import com.web3.exchange.auth.service.UserService;
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
    private final CaptchaService captchaService;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        try {
            // 1. 验证验证码
            validateCaptcha(request);

            // 2. 执行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 3. 设置安全上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 4. 获取用户信息
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

            // 5. 生成双令牌
            TokenPair tokenPair = jwtTokenProvider.generateTokenPair(authentication);

            // 6. 记录登录成功
            userService.recordLoginSuccess(
                    userPrincipal.getId(),
                    clientIp,
                    userAgent
            );

            // 7. 获取用户详情
            UserInfoResponse userInfo = userService.getUserInfo(userPrincipal.getId());

            // 8. 检查Access Token是否即将过期
            boolean needRefresh = jwtTokenProvider.isAccessTokenExpiringSoon(
                    tokenPair.getAccessToken()
            );
            tokenPair.setNeedRefresh(needRefresh);

            return LoginResponse.builder()
                    .accessToken(tokenPair.getAccessToken())
                    .refreshToken(tokenPair.getRefreshToken())
                    .tokenType(tokenPair.getTokenType())
                    .expiresIn(tokenPair.getAccessTokenExpiresIn())
                    .refreshExpiresIn(tokenPair.getRefreshTokenExpiresIn())
                    .userInfo(userInfo)
                    .needRefresh(needRefresh)
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
    public TokenResponse refreshAccessToken(String refreshToken) {
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
                // 标记Refresh Token为已使用
                jwtTokenProvider.markRefreshTokenUsed(refreshToken);
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

    /**
     * 验证验证码
     */
    private void validateCaptcha(LoginRequest request) {
        if (captchaService.isCaptchaEnabled()) {
            if (request.getCaptcha() == null || request.getCaptchaKey() == null) {
                throw new AuthException("验证码不能为空");
            }

            if (!captchaService.verifyCaptcha(request.getCaptchaKey(), request.getCaptcha())) {
                throw new AuthException("验证码错误");
            }
        }
    }
}