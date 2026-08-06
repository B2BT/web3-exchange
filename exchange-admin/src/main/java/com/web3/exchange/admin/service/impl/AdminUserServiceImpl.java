package com.web3.exchange.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.entity.AdminUser;
import com.web3.exchange.admin.mapper.AdminUserMapper;
import com.web3.exchange.admin.service.AdminUserService;
import com.web3.exchange.admin.vo.AdminUserVO;
import com.web3.exchange.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台用户管理服务实现：直接查 t_user（同库），分页 + 封禁/解封。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    /** 封禁状态：DISABLED */
    private static final int STATUS_DISABLED = 2;
    /** 正常状态 */
    private static final int STATUS_NORMAL = 1;

    private final AdminUserMapper adminUserMapper;

    public AdminUserServiceImpl(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public Page<AdminUserVO> pageUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<AdminUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(AdminUser::getUsername, keyword.trim())
                    .or().like(AdminUser::getPhone, keyword.trim()));
        }
        qw.orderByDesc(AdminUser::getId);
        Page<AdminUser> p = adminUserMapper.selectPage(new Page<>(page, size), qw);
        Page<AdminUserVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public void ban(Long id) {
        requireUser(id);
        adminUserMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .eq(AdminUser::getId, id)
                .set(AdminUser::getStatus, STATUS_DISABLED));
    }

    @Override
    public void unban(Long id) {
        requireUser(id);
        adminUserMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .eq(AdminUser::getId, id)
                .set(AdminUser::getStatus, STATUS_NORMAL));
    }

    private void requireUser(Long id) {
        if (adminUserMapper.selectById(id) == null) {
            throw new NotFoundException("用户不存在: " + id);
        }
    }

    private AdminUserVO toVO(AdminUser u) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setEmail(u.getEmail());
        vo.setPhone(u.getPhone());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        vo.setRegisterTime(u.getCreateTime());
        return vo;
    }
}
