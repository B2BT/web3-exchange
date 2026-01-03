package com.web3.exchange.user.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.user.entity.User;
import com.web3.exchange.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理",description = "所有用户相关接口")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/list")
    @Operation(summary = "查询用户列表")
    public Result<List<User>> getUserList(){
        List<User> list = userService.getUserList();
        return Result.success(list);
    }
}
