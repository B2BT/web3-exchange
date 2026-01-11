package com.web3.exchange.auth.controller;


import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.LoginResponse;
import com.web3.exchange.auth.dto.response.TokenPair;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

        return Result.success("登录成功", response);
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用Refresh Token刷新Access Token")
    public Result<TokenPair> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        TokenPair tokenPair = authService.refreshToken(request.getRefreshToken());

        return Result.success("令牌刷新成功", tokenPair);
    }

    /**
     * 仅刷新Access Token
     */
    @PostMapping("/refresh/access")
    @Operation(summary = "刷新Access Token", description = "仅刷新Access Token，Refresh Token不变")
    public Result<String> refreshAccessToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        String newAccessToken = authService.refreshAccessToken(request.getRefreshToken());

        return Result.success("Access Token刷新成功", newAccessToken);
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

        return Result.success("登出成功");
    }

    /**
     * 强制登出所有设备
     */
    @PostMapping("/logout/all")
    @Operation(summary = "强制所有设备登出", description = "使当前用户的所有令牌失效")
    public Result<Void> logoutAll() {
        Long userId = SecurityUtils.getUserId();
        authService.logoutAll(userId);

        return Result.success("所有设备已登出");
    }

    /**
     * 验证令牌
     */
    @PostMapping("/validate")
    @Operation(summary = "验证令牌", description = "验证Access Token是否有效")
    public Result<Boolean> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        String token = extractToken(authHeader);
        boolean valid = token != null && jwtTokenProvider.validateAccessToken(token);

        return Result.success(Map.of("valid", valid));
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
}