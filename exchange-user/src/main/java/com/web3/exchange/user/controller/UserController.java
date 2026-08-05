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

/**
 * 用户服务 REST 接口（/api/users/**）——用户资料、注册、KYC、2FA、密码、等级。
 * <p>
 * 接口分层：注册与更新密码会被 auth 服务（8102）通过 Feign 调用（P3-B），因此路径/入参
 * 需保持稳定；KYC/2FA/等级/资料等接口对外经网关鉴权后供前端使用。
 * 真正的落库与业务逻辑委托给 {@link UserService}。
 * </p>
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理",description = "所有用户相关接口")
public class UserController {
    @Autowired
    private UserService userService;

    /**
     * 查询用户列表（管理/调试用途，返回脱敏后的非敏感字段）。
     */
    @GetMapping("/list")
    @Operation(summary = "查询用户列表")
    public Result<List<UserVO>> getUserList(){
        List<UserVO> list = userService.getUserList();
        return Result.success(list);
    }

    /**
     * 获取用户信息并鉴权：供 auth 服务登录时通过 Feign 拉取用户详情（含加密密码、2FA 密钥等）。
     */
    @Operation(summary = "获取用户信息，鉴权")
    @GetMapping("/info/{username}")
    public Result<UserDetailDTO> getUserInfo(@PathVariable("username") String username){
        return Result.success(userService.getUserInfo(username));
    }

    /**
     * 注册新用户。
     * <p>业务要点：校验 username/email/phone 唯一性、BCrypt 编码密码、处理邀请码（裂变）、
     * 生成自身邀请码、设默认等级 NORMAL、KYC 与 2FA 初始为未开启。
     * 该接口会被 auth 服务注册接口(P3-B)通过 Feign 调用，保持路径/入参稳定。</p>
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
     * 提交 KYC 实名认证。
     * <p>业务要点：交易所合规要求必须实名；提交后置 KYC 状态为「审核中」，
     * 已审核通过/审核中的用户不可重复提交。</p>
     */
    @PostMapping("/{id}/kyc")
    @Operation(summary = "提交KYC实名认证")
    public Result<Void> submitKyc(@PathVariable("id") Long id,
                                  @Valid @RequestBody KYCSubmitDTO dto){
        userService.submitKyc(id, dto);
        return Result.success();
    }

    /**
     * 查询 KYC 认证状态（状态码 + 认证等级）。
     */
    @GetMapping("/{id}/kyc/status")
    @Operation(summary = "查询KYC认证状态")
    public Result<KycStatusVO> getKycStatus(@PathVariable("id") Long id){
        return Result.success(userService.getKycStatus(id));
    }

    /**
     * 更新用户密码（BCrypt 加密）。
     * <p>供 auth 服务修改/重置密码(P3-B)通过 Feign 调用；真正的密码校验（如原密码比对）
     * 在 auth 端完成，本接口只负责落库加密。</p>
     */
    @PutMapping("/{id}/password")
    @Operation(summary = "更新用户密码")
    public Result<Void> updatePassword(@PathVariable("id") Long id,
                                       @Valid @RequestBody PasswordUpdateDTO dto){
        userService.updatePassword(id, dto.getNewPassword());
        return Result.success();
    }

    /**
     * 开启 2FA。
     * <p>业务要点：生成 base32 密钥返回给前端用于生成 Google Authenticator 二维码；
     * 已开启的用户返回现有密钥、不重复生成。开启后登录须校验 TOTP 动态码（防账号被盗）。</p>
     */
    @PostMapping("/{id}/2fa/enable")
    @Operation(summary = "开启2FA")
    public Result<TwoFactorEnableVO> enableTwoFactor(@PathVariable("id") Long id){
        return Result.success(userService.enableTwoFactor(id));
    }

    /**
     * 查询用户等级（NORMAL/VIP/SVIP），用于差异化权益与费率。
     */
    @GetMapping("/{id}/level")
    @Operation(summary = "查询用户等级")
    public Result<String> getUserLevel(@PathVariable("id") Long id){
        return Result.success(userService.getUserLevel(id));
    }
}
