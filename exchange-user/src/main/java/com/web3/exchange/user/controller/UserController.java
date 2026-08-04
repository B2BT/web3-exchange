package com.web3.exchange.user.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.common.user.UserDetailDTO;
import com.web3.exchange.user.dto.KYCSubmitDTO;
import com.web3.exchange.user.dto.PasswordUpdateDTO;
import com.web3.exchange.user.dto.RegisterDTO;
import com.web3.exchange.user.dto.UpdateProfileDTO;
import com.web3.exchange.user.service.UserService;
import com.web3.exchange.user.vo.KycStatusVO;
import com.web3.exchange.user.vo.TwoFactorEnableVO;
import com.web3.exchange.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理",description = "所有用户相关接口")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/list")
    @Operation(summary = "查询用户列表")
    public Result<List<UserVO>> getUserList(){
        List<UserVO> list = userService.getUserList();
        return Result.success(list);
    }
    @Operation(summary = "获取用户信息，鉴权")
    @GetMapping("/info/{username}")
    public Result<UserDetailDTO> getUserInfo(@PathVariable("username") String username){
        return Result.success(userService.getUserInfo(username));
    }

    /**
     * 注册新用户
     * <p>该接口会被 auth 服务注册接口(P3-B)通过 Feign 调用，保持路径/入参稳定。</p>
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto){
        return Result.success(userService.register(dto));
    }

    /**
     * 修改用户资料（昵称/邮箱/手机号/头像/真实姓名）
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改用户资料")
    public Result<UserVO> updateProfile(@PathVariable("id") Long id,
                                        @Valid @RequestBody UpdateProfileDTO dto){
        return Result.success(userService.updateProfile(id, dto));
    }

    /**
     * 提交 KYC 实名认证
     */
    @PostMapping("/{id}/kyc")
    @Operation(summary = "提交KYC实名认证")
    public Result<Void> submitKyc(@PathVariable("id") Long id,
                                  @Valid @RequestBody KYCSubmitDTO dto){
        userService.submitKyc(id, dto);
        return Result.success();
    }

    /**
     * 查询 KYC 认证状态
     */
    @GetMapping("/{id}/kyc/status")
    @Operation(summary = "查询KYC认证状态")
    public Result<KycStatusVO> getKycStatus(@PathVariable("id") Long id){
        return Result.success(userService.getKycStatus(id));
    }

    /**
     * 更新用户密码（BCrypt 加密）
     * <p>供 auth 服务修改/重置密码(P3-B)通过 Feign 调用。</p>
     */
    @PutMapping("/{id}/password")
    @Operation(summary = "更新用户密码")
    public Result<Void> updatePassword(@PathVariable("id") Long id,
                                       @Valid @RequestBody PasswordUpdateDTO dto){
        userService.updatePassword(id, dto.getNewPassword());
        return Result.success();
    }

    /**
     * 开启 2FA，返回 base32 密钥供前端生成二维码
     */
    @PostMapping("/{id}/2fa/enable")
    @Operation(summary = "开启2FA")
    public Result<TwoFactorEnableVO> enableTwoFactor(@PathVariable("id") Long id){
        return Result.success(userService.enableTwoFactor(id));
    }

    /**
     * 查询用户等级
     */
    @GetMapping("/{id}/level")
    @Operation(summary = "查询用户等级")
    public Result<String> getUserLevel(@PathVariable("id") Long id){
        return Result.success(userService.getUserLevel(id));
    }
}
