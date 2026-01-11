package com.web3.exchange.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.common.user.UserDetailDTO;
import com.web3.exchange.user.entity.User;
import com.web3.exchange.user.mapper.UserMapper;
import com.web3.exchange.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public List<User> getUserList() {
        return list();
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
