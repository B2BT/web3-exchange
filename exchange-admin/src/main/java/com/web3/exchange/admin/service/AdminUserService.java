package com.web3.exchange.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.vo.AdminUserVO;

/**
 * 后台用户管理服务：全站分页查询 + 封禁/解封。
 */
public interface AdminUserService {

    /** 分页用户（keyword 模糊 username/phone）。 */
    Page<AdminUserVO> pageUsers(int page, int size, String keyword);

    /** 封禁（status=2 DISABLED）。 */
    void ban(Long id);

    /** 解封（status=1）。 */
    void unban(Long id);
}
