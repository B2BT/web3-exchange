package com.web3.exchange.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.user.UserDetailDTO;
import com.web3.exchange.user.dto.KYCSubmitDTO;
import com.web3.exchange.user.dto.RegisterDTO;
import com.web3.exchange.user.dto.UpdateProfileDTO;
import com.web3.exchange.user.entity.User;
import com.web3.exchange.user.mapper.UserMapper;
import com.web3.exchange.user.service.UserService;
import com.web3.exchange.user.vo.KycStatusVO;
import com.web3.exchange.user.vo.TwoFactorEnableVO;
import com.web3.exchange.user.vo.UserVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现——用户注册、资料、KYC、2FA、密码、等级的落库逻辑。
 * <p>
 * 设计要点：
 * ①注册保证 username/email/phone 唯一，密码 BCrypt 加密后落库，处理邀请码裂变并生成自身邀请码；
 * ②KYC 提交后置「审核中」状态，已审核/审核中不可重复提交，满足交易所合规实名要求；
 * ③2FA 密钥按 RFC 4648 base32 生成、与 Google Authenticator 兼容，已开启则返回现有密钥；
 * ④密码更新由 auth 服务经 Feign 触发，本实现只负责 BCrypt 编码落库与记录更新时间。
 * 对外暴露的 VO 均经 {@link #toUserVO} 脱敏，避免泄露敏感字段。
 * </p>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String DEFAULT_LEVEL = "NORMAL";
    private static final int DEFAULT_STATUS = 1;

    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    // RFC 4648 base32 字母表(A-Z + 2-7，不含 0/1/8/9/I/O)，用于 2FA secret
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public List<UserVO> getUserList() {
        return list().stream()
                .map(this::toUserVO)
                .collect(Collectors.toList());
    }

    /**
     * 注册新用户。
     * <p>流程：唯一性校验（username/email/phone）→ 处理邀请码（无效则拒绝）→ BCrypt 编码密码 →
     * 生成自身唯一邀请码 → 设默认状态/等级 NORMAL、KYC 与 2FA 未开启 → 落库。
     * 全部在同一事务内完成，任一环节失败整体回滚。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterDTO dto) {
        // 1. 唯一性校验
        checkUnique("username", dto.getUsername(), null);
        checkUnique("email", dto.getEmail(), null);
        checkUnique("phone", dto.getPhone(), null);

        // 2. 处理邀请码
        Long invitedBy = null;
        String inviteCode = dto.getInviteCode();
        if (inviteCode != null && !inviteCode.isBlank()) {
            User inviter = this.baseMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getInviteCode, inviteCode.trim())
                            .last("limit 1"));
            if (inviter == null) {
                throw new BusinessException("邀请码不存在或无效");
            }
            invitedBy = inviter.getId();
        }

        // 3. 组装用户实体
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        user.setStatus(DEFAULT_STATUS);
        user.setAccountNonExpired(1);
        user.setAccountNonLocked(1);
        user.setCredentialsNonExpired(1);
        user.setEnabled(1);
        user.setUserLevel(DEFAULT_LEVEL);
        user.setInviteCode(generateUniqueInviteCode());
        user.setInvitedBy(invitedBy != null ? String.valueOf(invitedBy) : null);
        user.setRegisterSource(dto.getRegisterSource() != null ? dto.getRegisterSource() : "web");
        user.setRegisterIp(dto.getRegisterIp());
        user.setKycStatus(0);
        user.setKycLevel(0);
        user.setTwoFactorEnabled(0);

        // 4. 保存
        boolean saved = this.save(user);
        if (!saved) {
            throw new BusinessException("注册失败，请稍后重试");
        }

        return toUserVO(user);
    }

    /**
     * 修改用户资料（昵称/邮箱/手机/头像/真实姓名）。
     * <p>邮箱/手机改动前做唯一性校验（排除自身），避免与他人冲突；动态拼接更新字段。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long id, UpdateProfileDTO dto) {
        User user = requireUser(id);

        // 唯一性校验（排除自身）
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            checkUnique("email", dto.getEmail(), id);
        }
        if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
            checkUnique("phone", dto.getPhone(), id);
        }

        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
        uw.eq(User::getId, id);
        if (dto.getNickname() != null) {
            uw.set(User::getNickname, dto.getNickname());
        }
        if (dto.getEmail() != null) {
            uw.set(User::getEmail, dto.getEmail());
        }
        if (dto.getPhone() != null) {
            uw.set(User::getPhone, dto.getPhone());
        }
        if (dto.getAvatar() != null) {
            uw.set(User::getAvatar, dto.getAvatar());
        }
        if (dto.getRealName() != null) {
            uw.set(User::getRealName, dto.getRealName());
        }

        this.update(uw);
        return toUserVO(requireUser(id));
    }

    /**
     * 提交 KYC 实名认证。
     * <p>业务规则：已「审核中」(1) 或「已通过」(2) 的用户禁止重复提交；提交后写入实名信息
     * （姓名/证件类型/证件号/正反照）并置状态为「审核中」、认证等级默认 1。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitKyc(Long id, KYCSubmitDTO dto) {
        User user = requireUser(id);

        if (Integer.valueOf(1).equals(user.getKycStatus())) {
            throw new BusinessException("KYC认证审核中，请勿重复提交");
        }
        if (Integer.valueOf(2).equals(user.getKycStatus())) {
            throw new BusinessException("KYC已认证通过");
        }

        int kycLevel = (user.getKycLevel() == null || user.getKycLevel() == 0) ? 1 : user.getKycLevel();
        this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getRealName, dto.getRealName())
                .set(User::getIdCardType, dto.getIdCardType())
                .set(User::getIdCardNo, dto.getIdCardNo())
                .set(User::getIdCardFront, dto.getIdCardFront())
                .set(User::getIdCardBack, dto.getIdCardBack())
                .set(User::getKycStatus, 1)
                .set(User::getKycLevel, kycLevel));
    }

    @Override
    public KycStatusVO getKycStatus(Long id) {
        User user = requireUser(id);
        return new KycStatusVO(user.getKycStatus(), user.getKycLevel());
    }

    /**
     * 开启 2FA。
     * <p>业务要点：若已开启且有密钥则直接返回现有密钥（不重复生成，保证前端二维码与
     * 后端密钥一致）；否则生成 RFC 4648 base32 密钥、置 twoFactorEnabled=1 并落库，
     * 返回密钥供前端生成 Google Authenticator 二维码。开启后登录须校验 TOTP 动态码。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TwoFactorEnableVO enableTwoFactor(Long id) {
        User user = requireUser(id);

        // 若已开启，返回现有密钥，不重复生成
        if (Integer.valueOf(1).equals(user.getTwoFactorEnabled())
                && user.getSecretKey() != null && !user.getSecretKey().isBlank()) {
            return new TwoFactorEnableVO(user.getSecretKey(), true);
        }

        String secret = generateBase32Secret();
        this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getSecretKey, secret)
                .set(User::getTwoFactorEnabled, 1)
                .set(User::getTwoFactorType, "google"));
        return new TwoFactorEnableVO(secret, true);
    }

    @Override
    public String getUserLevel(Long id) {
        User user = requireUser(id);
        return user.getUserLevel();
    }

    /**
     * 更新用户密码（BCrypt 加密）
     * <p>供 auth 服务修改/重置密码(P3-B)通过 Feign 调用。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long id, String newPassword) {
        requireUser(id);
        String encoded = passwordEncoder.encode(newPassword);
        this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getPassword, encoded)
                .set(User::getPasswordUpdateTime, LocalDateTime.now()));
    }

    // ==================== 私有方法 ====================

    private User requireUser(Long id) {
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: " + id);
        }
        return user;
    }

    /**
     * 校验字段唯一性（排除指定 id）
     */
    private void checkUnique(String field, Object value, Long excludeId) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            return;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        switch (field) {
            case "username" -> wrapper.eq(User::getUsername, value);
            case "email" -> wrapper.eq(User::getEmail, value);
            case "phone" -> wrapper.eq(User::getPhone, value);
            default -> throw new IllegalArgumentException("不支持的唯一性字段: " + field);
        }
        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }
        Long count = this.baseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("该" + field + "已被使用");
        }
    }

    /**
     * 生成全局唯一邀请码
     */
    private String generateUniqueInviteCode() {
        for (int i = 0; i < 20; i++) {
            String code = randomString(8);
            Long count = this.baseMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getInviteCode, code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BusinessException("邀请码生成失败，请稍后重试");
    }

    /**
     * 生成随机字符串（邀请码）
     */
    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(INVITE_CHARS.charAt(secureRandom.nextInt(INVITE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 生成 RFC 4648 base32 格式的 2FA 密钥（Google Authenticator 兼容）
     */
    private String generateBase32Secret() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(BASE32_CHARS.charAt(secureRandom.nextInt(BASE32_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 脱敏转换：仅保留非敏感展示字段
     */
    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setTwoFactorEnabled(user.getTwoFactorEnabled());
        vo.setUserLevel(user.getUserLevel());
        vo.setRegisterSource(user.getRegisterSource());
        vo.setInviteCode(user.getInviteCode());
        vo.setKycStatus(user.getKycStatus());
        vo.setKycLevel(user.getKycLevel());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    @Override
    public UserDetailDTO getUserInfo(String username) {
        User user = this.baseMapper.
                selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username).last("limit 1"));
        return userToDetailDTO(user);
    }

    private UserDetailDTO userToDetailDTO(User user) {
        return UserDetailDTO.builder()
                .password(user.getPassword())
                .salt(user.getSecretKey())
                .twoFactorEnabled(user.getTwoFactorEnabled())
                .secretKey(user.getSecretKey())
                .loginFailureCount(user.getLoginFailCount())
                .lastLoginTime(user.getLastLoginTime())
                .lastLoginIp(user.getLastLoginIp())
                .id(user.getId()).username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .deptId(user.getTenantId())
                .tenantId(user.getTenantId())
//                .roles()
//                .permissions()
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }
}
