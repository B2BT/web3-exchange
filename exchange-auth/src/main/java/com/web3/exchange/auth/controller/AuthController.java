package com.web3.exchange.auth.controller;


import com.web3.exchange.auth.dto.request.ChangePasswordRequest;
import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.request.RefreshTokenRequest;
import com.web3.exchange.auth.dto.request.RegisterRequest;
import com.web3.exchange.auth.dto.request.ResetPasswordRequest;
import com.web3.exchange.auth.dto.response.CaptchaResponse;
import com.web3.exchange.auth.dto.response.LoginResponse;
import com.web3.exchange.auth.dto.response.TokenPair;
import com.web3.exchange.auth.security.jwt.JwtTokenProvider;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.auth.service.CaptchaService;
import com.web3.exchange.common.exception.AuthException;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "认证管理", description = "用户认证相关接口")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CaptchaService captchaService;

    /**
     * 获取图形验证码
     */
    @GetMapping("/captcha")
    @Operation(summary = "获取图形验证码", description = "返回 captchaId 与算式/答案，登录/注册需携带")
    public Result<CaptchaResponse> captcha() {
        return Result.success(captchaService.generate());
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "校验验证码后调 user 服务注册")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    /**
     * 修改密码（需登录）
     */
    @PostMapping("/change-password")
    @Operation(summary = "修改密码", description = "校验原密码后更新为新密码，需携带登录 Access Token")
    public Result<Void> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ChangePasswordRequest request) {
        String username = currentUsername(authHeader);
        authService.changePassword(username, request);
        return Result.success();
    }

    /**
     * 重置密码（生产环境需补充短信/邮箱验证码）
     */
    @PostMapping("/reset-password")
    @Operation(summary = "重置密码", description = "按用户名/邮箱重置密码（本次简化实现，生产应加短信/邮箱验证码）")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.success();
    }

    /**
     * 登录接口
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名密码登录，返回双令牌")
    public Result<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("登录请求 - 用户: {}, IP: {}", request.getUsername(), clientIp);

        LoginResponse response = authService.login(request, clientIp, userAgent);

        log.info("登录成功 - 用户ID: {}", response.getUserInfo().getId());

        return Result.success(response);
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用Refresh Token刷新双令牌")
    public Result<TokenPair> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        TokenPair tokenPair = authService.refreshToken(request.getRefreshToken());

        return Result.success(tokenPair);
    }

    /**
     * 仅刷新Access Token
     */
    @PostMapping("/refresh/access")
    @Operation(summary = "刷新Access Token", description = "仅刷新Access Token，Refresh Token不变")
    public Result<String> refreshAccessToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        String newAccessToken = authService.refreshAccessToken(request.getRefreshToken());

        return Result.success(newAccessToken);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "注销当前用户，使令牌失效")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) RefreshTokenRequest request) {

        String accessToken = extractToken(authHeader);
        String refreshToken = request != null ? request.getRefreshToken() : null;

        authService.logout(accessToken, refreshToken);

        return Result.success();
    }

    /**
     * 强制登出所有设备
     */
    @PostMapping("/logout/all")
    @Operation(summary = "强制所有设备登出", description = "使当前用户的所有令牌失效")
    public Result<Void> logoutAll(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            throw new AuthException("无法识别当前用户，缺少 X-User-Id 请求头");
        }

        authService.logoutAll(userId);

        return Result.success();
    }

    /**
     * 验证令牌
     */
    @PostMapping("/validate")
    @Operation(summary = "验证令牌", description = "验证Access Token是否有效")
    public Result<Boolean> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        // 传入完整 Bearer 头，由 JwtTokenProvider 完成前缀剥离与校验
        boolean valid = authHeader != null && jwtTokenProvider.validateAccessToken(authHeader);

        return Result.success(valid);
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * 从 Authorization 头解析当前登录用户名
     */
    private String currentUsername(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new AuthException("未登录，缺少 Authorization 请求头");
        }
        try {
            return jwtTokenProvider.getUsernameFromAccessToken(authHeader);
        } catch (Exception e) {
            log.error("解析当前用户名失败", e);
            throw new AuthException("无效的 Access Token");
        }
    }
}
