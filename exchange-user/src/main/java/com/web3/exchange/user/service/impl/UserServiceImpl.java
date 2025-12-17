package com.web3.exchange.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
}
