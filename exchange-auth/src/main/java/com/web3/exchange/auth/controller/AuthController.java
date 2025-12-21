package com.web3.exchange.auth.controller;

import com.web3.exchange.auth.dto.request.LoginRequest;
import com.web3.exchange.auth.dto.response.AuthResponse;
import com.web3.exchange.auth.dto.response.UserInfoResponse;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录接口
     * // @Valid开启数据校验
     * // @RequestBody 将请求体的JSON字符串自动转换成Java对象
     * @param loginRequest
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.login(loginRequest);
        return Result.success(response,"登录成功");
    }
    /**
     * 获取当前用户
     * @param token
     * @return
     */
    @PostMapping("/current-user")
    @Operation(summary = "获取当前用户")
    public Result<UserInfoResponse> getCurrentUser(@RequestHeader("Authorization")String token) {
        UserInfoResponse currentUser = authService.getUserFromToken(token);
        return Result.success(currentUser);
    }

    /**
     * 用户注销接口
     * @param token
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "用户注销")
    public Result logout(@RequestHeader("Authorization")String token) {
        authService.logout(token);
        return Result.success("注销成功");
    }
}

