package com.web3.exchange.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.common.user.UserDetailDTO;
import com.web3.exchange.user.entity.User;
import com.web3.exchange.user.vo.UserVO;

import java.util.List;

public interface UserService extends IService<User>  {
    /**
     * 获取用户列表（脱敏）
     */
    List<UserVO> getUserList();

    UserDetailDTO getUserInfo(String username);
}
