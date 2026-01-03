package com.web3.exchange.auth.service;

import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.AuthResponse;
import com.web3.exchange.auth.dto.response.TokenResponse;
import com.web3.exchange.auth.dto.response.UserInfoResponse;

public interface AuthService {
    /**
     * 用户登陆
     * @param loginRequest
     * @return
     */
    AuthResponse login(LoginRequest loginRequest);

    /**
     * 刷新访问令牌
     * @param refreshToken
     * @return
     */
    TokenResponse refreshToken(String refreshToken);

    /**
     * 用户登出
     * @param token
     */
    void logout(String token);

    /**
     * 验证令牌
     * @param token
     * @return
     */
    boolean validateToken(String token);

    /**
     * 获取当前登录用户信息
     * @return
     */
    UserInfoResponse getCurrentUserInfo();
}
