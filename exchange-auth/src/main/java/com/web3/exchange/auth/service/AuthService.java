package com.web3.exchange.auth.service;

import com.web3.exchange.auth.dto.request.LoginRequest;
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
