package com.web3.exchange.auth.service;

import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.AuthResponse;
import com.web3.exchange.auth.dto.response.TokenResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    TokenResponse refreshToken(String refreshToken);

    void logout(String accessToken);

    boolean validateToken(String token);
}
