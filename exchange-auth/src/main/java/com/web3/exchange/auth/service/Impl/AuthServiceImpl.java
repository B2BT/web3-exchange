package com.web3.exchange.auth.service.Impl;

import com.web3.exchange.auth.config.CaptchaProperties;
import com.web3.exchange.auth.dto.request.ChangePasswordRequest;
import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.request.RegisterRequest;
import com.web3.exchange.auth.dto.request.ResetPasswordRequest;
import com.web3.exchange.auth.dto.response.LoginResponse;
import com.web3.exchange.auth.dto.response.TokenPair;
import com.web3.exchange.auth.dto.response.UserInfoResponse;
import com.web3.exchange.auth.feign.UserServiceClient;
import com.web3.exchange.auth.security.domain.UserPrincipal;
import com.web3.exchange.auth.security.jwt.JwtTokenProvider;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.auth.service.CaptchaService;
import com.web3.exchange.auth.util.TotpUtil;
import com.web3.exchange.common.exception.AuthException;
import com.web3.exchange.common.exception.ServiceException;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.common.user.UserDetailDTO;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final CaptchaService captchaService;
    private final CaptchaProperties captchaProperties;
    private final PasswordEncoder passwordEncoder;
    private final UserServiceClient userServiceClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        // 0. 图形验证码校验（开关 captcha.enabled）
        if (captchaProperties.isEnabled()) {
            if (!captchaService.verify(request.getCaptchaId(), request.getCaptcha())) {
                throw new AuthException("验证码错误或已过期");
            }
        }

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

            // 4. 2FA 校验：用户开启2FA后必须提供正确的 TOTP 动态码
            if (Integer.valueOf(1).equals(userPrincipal.getTwoFactorEnabled())) {
                if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
                    throw new AuthException("需要2FA验证码");
                }
                if (!TotpUtil.verify(userPrincipal.getSecretKey(), request.getTotpCode())) {
                    throw new AuthException("2FA验证码错误");
                }
            }

            // 5. 生成双令牌
            TokenPair tokenPair = jwtTokenProvider.generateTokenPair(authentication);

            // 6. 检查Access Token是否即将过期
            boolean needRefresh = jwtTokenProvider.isAccessTokenExpiringSoon(
                    tokenPair.getAccessToken()
            );
            tokenPair.setNeedRefresh(needRefresh);

            // 7. 根据认证主体构造用户信息响应
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

        } catch (AuthException e) {
            // 2FA 校验失败等业务性错误：原样抛出，不包装
            throw e;
        } catch (Exception e) {
            log.error("登录失败", e);
            throw new AuthException("登录失败: " + e.getMessage());
        }
    }

    @Override
    public void register(RegisterRequest request) {
        // 1. 验证码校验
        if (captchaProperties.isEnabled()) {
            if (!captchaService.verify(request.getCaptchaId(), request.getCaptcha())) {
                throw new AuthException("验证码错误或已过期");
            }
        }

        // 2. 组装与 user 服务 RegisterDTO 对齐的入参
        Map<String, Object> dto = new HashMap<>();
        dto.put("username", request.getUsername());
        dto.put("password", request.getPassword());
        dto.put("email", request.getEmail());
        dto.put("phone", request.getPhone());
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            dto.put("nickname", request.getNickname());
        }
        if (request.getInviteCode() != null && !request.getInviteCode().isBlank()) {
            dto.put("inviteCode", request.getInviteCode());
        }
        dto.put("registerSource", request.getSource() != null ? request.getSource() : "web");
        if (request.getRegisterIp() != null && !request.getRegisterIp().isBlank()) {
            dto.put("registerIp", request.getRegisterIp());
        }

        // 3. 调 user 服务注册
        try {
            Result<Void> result = userServiceClient.register(dto);
            if (result == null || !result.isSuccess()) {
                String msg = result != null && result.getMessage() != null
                        ? result.getMessage() : "注册失败";
                throw new ServiceException(msg);
            }
        } catch (FeignException e) {
            log.error("注册调用 user 服务失败", e);
            throw new ServiceException(extractFeignMessage(e, "注册失败"));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("注册异常", e);
            throw new ServiceException("注册失败: " + e.getMessage());
        }
    }

    @Override
    public void changePassword(String username, ChangePasswordRequest request) {
        // 1. 加载当前用户（获取已加密密码）
        UserDetailDTO user = loadUserDetail(username);

        // 2. 校验原密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AuthException("原密码错误");
        }

        // 3. 编码新密码并更新
        doUpdatePassword(user.getId(), request.getNewPassword());
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        // 说明：生产环境应在此处校验短信/邮箱验证码（request.code / codeType），本次简化实现不校验。
        String username = request.getUsernameOrEmail();
        UserDetailDTO user = loadUserDetail(username);
        doUpdatePassword(user.getId(), request.getNewPassword());
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

    // ==================== 私有方法 ====================

    /**
     * 通过 Feign 加载用户详情
     */
    private UserDetailDTO loadUserDetail(String username) {
        try {
            Result<UserDetailDTO> result = userServiceClient.getUserInfo(username);
            UserDetailDTO user = result != null ? result.getData() : null;
            if (user == null) {
                throw new AuthException("用户不存在: " + username);
            }
            return user;
        } catch (FeignException e) {
            log.error("加载用户[{}]失败", username, e);
            throw new AuthException("用户不存在: " + username);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载用户[{}]异常", username, e);
            throw new AuthException("用户不存在: " + username);
        }
    }

    /**
     * 调用 user 服务更新密码
     */
    private void doUpdatePassword(Long userId, String newPassword) {
        Map<String, String> body = new HashMap<>();
        body.put("newPassword", newPassword);
        try {
            Result<Void> result = userServiceClient.updatePassword(userId, body);
            if (result == null || !result.isSuccess()) {
                String msg = result != null && result.getMessage() != null
                        ? result.getMessage() : "密码更新失败";
                throw new ServiceException(msg);
            }
        } catch (FeignException e) {
            log.error("更新密码调用 user 服务失败", e);
            throw new ServiceException(extractFeignMessage(e, "密码更新失败"));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新密码异常", e);
            throw new ServiceException("密码更新失败: " + e.getMessage());
        }
    }

    /**
     * 从 FeignException 中尝试提取 user 服务返回的业务错误信息
     */
    private String extractFeignMessage(FeignException e, String fallback) {
        try {
            if (e.contentUTF8() != null && !e.contentUTF8().isBlank()) {
                // 尝试从 JSON 中粗提取 message 字段
                String body = e.contentUTF8();
                int idx = body.indexOf("\"message\"");
                if (idx >= 0) {
                    int colon = body.indexOf(':', idx);
                    int start = body.indexOf('"', colon + 1);
                    int end = body.indexOf('"', start + 1);
                    if (start >= 0 && end > start) {
                        return body.substring(start + 1, end);
                    }
                }
            }
        } catch (Exception ignored) {
            // 忽略解析失败，返回兜底
        }
        return fallback;
    }
}
