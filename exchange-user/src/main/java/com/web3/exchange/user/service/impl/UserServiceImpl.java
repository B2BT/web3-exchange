package com.web3.exchange.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.common.user.UserDetailDTO;
import com.web3.exchange.user.entity.User;
import com.web3.exchange.user.mapper.UserMapper;
import com.web3.exchange.user.service.UserService;
import com.web3.exchange.user.vo.UserVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public List<UserVO> getUserList() {
        return list().stream()
                .map(this::toUserVO)
                .collect(Collectors.toList());
    }

    /**
     * 脱敏转换：仅保留非敏感展示字段
     */
    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setTwoFactorEnabled(user.getTwoFactorEnabled());
        vo.setUserLevel(user.getUserLevel());
        vo.setRegisterSource(user.getRegisterSource());
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
