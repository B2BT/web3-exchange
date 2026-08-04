package com.web3.exchange.auth.service;

import com.web3.exchange.auth.dto.request.ChangePasswordRequest;
import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.request.RegisterRequest;
import com.web3.exchange.auth.dto.request.ResetPasswordRequest;
import com.web3.exchange.auth.dto.response.LoginResponse;
import com.web3.exchange.auth.dto.response.TokenPair;

public interface AuthService {
    /**
     * 用户登陆
     * @param request
     * @param clientIp
     * @param userAgent
     * @return
     */
    LoginResponse login(LoginRequest request, String clientIp, String userAgent);

    /**
     * 用户注册（校验验证码后经 Feign 调 user 服务）
     */
    void register(RegisterRequest request);

    /**
     * 修改密码（校验原密码）
     *
     * @param username 当前登录用户名
     * @param request  原密码/新密码
     */
    void changePassword(String username, ChangePasswordRequest request);

    /**
     * 重置密码（生产环境应补充短信/邮箱验证码校验）
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * 刷新双令牌
     * @param refreshToken
     * @return
     */
    TokenPair refreshToken(String refreshToken);

    /**
     * 仅刷新访问令牌（Refresh Token不变）
     * @param refreshToken
     * @return 新的 Access Token
     */
    String refreshAccessToken(String refreshToken);

    /**
     * 用户登出
     * @param accessToken
     * @param refreshToken
     */
    void logout(String accessToken, String refreshToken);

    /**
     * 强制所有设备登出
     * @param userId
     */
    void logoutAll(Long userId);

    /**
     * 验证 Access Token
     * @param accessToken
     * @return
     */
    boolean validateToken(String accessToken);
}
