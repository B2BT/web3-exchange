package com.web3.exchange.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.common.user.UserDetailDTO;
import com.web3.exchange.user.dto.KYCSubmitDTO;
import com.web3.exchange.user.dto.RegisterDTO;
import com.web3.exchange.user.dto.UpdateProfileDTO;
import com.web3.exchange.user.entity.User;
import com.web3.exchange.user.vo.KycStatusVO;
import com.web3.exchange.user.vo.TwoFactorEnableVO;
import com.web3.exchange.user.vo.UserVO;

import java.util.List;

public interface UserService extends IService<User>  {
    /**
     * 获取用户列表（脱敏）
     */
    List<UserVO> getUserList();

    UserDetailDTO getUserInfo(String username);

    /**
     * 注册新用户
     * <p>校验用户名/邮箱/手机号唯一性、BCrypt 加密密码、处理邀请码、生成邀请码、设置默认等级与状态。</p>
     *
     * @param dto 注册请求
     * @return 注册后的用户信息（脱敏）
     */
    UserVO register(RegisterDTO dto);

    /**
     * 修改用户资料（昵称/邮箱/手机号/头像/真实姓名）
     *
     * @param id  用户ID
     * @param dto 修改请求
     * @return 修改后的用户信息（脱敏）
     */
    UserVO updateProfile(Long id, UpdateProfileDTO dto);

    /**
     * 提交 KYC 实名认证
     *
     * @param id  用户ID
     * @param dto KYC提交请求
     */
    void submitKyc(Long id, KYCSubmitDTO dto);

    /**
     * 查询 KYC 认证状态
     *
     * @param id 用户ID
     * @return KYC状态
     */
    KycStatusVO getKycStatus(Long id);

    /**
     * 开启 2FA，生成并保存密钥
     *
     * @param id 用户ID
     * @return 2FA密钥（base32），供前端生成二维码
     */
    TwoFactorEnableVO enableTwoFactor(Long id);

    /**
     * 查询用户等级
     *
     * @param id 用户ID
     * @return 用户等级
     */
    String getUserLevel(Long id);

    /**
     * 更新用户密码（BCrypt 加密）
     * <p>供 auth 服务修改/重置密码(P3-B)通过 Feign 调用。</p>
     *
     * @param id          用户ID
     * @param newPassword 明文新密码（内部会 BCrypt 加密）
     */
    void updatePassword(Long id, String newPassword);
}
