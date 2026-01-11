package com.web3.exchange.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.common.user.UserDetailDTO;
import com.web3.exchange.user.entity.User;

import java.util.List;

public interface UserService extends IService<User>  {
    /**
     * 获取用户列表
     */
    List<User> getUserList();

    UserDetailDTO getUserInfo(String username);
}
