package com.web3.exchange.auth.service;

import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.LoginResponse;
import com.web3.exchange.auth.dto.response.TokenResponse;
import com.web3.exchange.auth.dto.response.UserInfoResponse;

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
     * 刷新访问令牌
     * @param refreshToken
     * @return
     */
    TokenResponse refreshAccessToken(String refreshToken);

    /**
     * 用户登出
     * @param token
     */
    void logout(String token);

    /**
     * 验证令牌
     * @param accessToken
     * @param refreshToken
     * @return
     */
    boolean validateToken(String accessToken, String refreshToken);

    public void logoutAll(Long userId);
}
