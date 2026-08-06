package com.web3.exchange.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.service.AdminUserService;
import com.web3.exchange.admin.vo.AdminUserVO;
import com.web3.exchange.common.model.PageData;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理接口（/api/admin/user/**，需 ADMIN 角色）。
 */
@RestController
@RequestMapping("/api/admin/user")
@Tag(name = "后台用户管理")
public class UserAdminController {

    private final AdminUserService adminUserService;

    public UserAdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "分页查询用户")
    @GetMapping("/list")
    public Result<PageData<AdminUserVO>> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                              @RequestParam(value = "size", defaultValue = "20") int size,
                                              @RequestParam(value = "keyword", required = false) String keyword) {
        Page<AdminUserVO> p = adminUserService.pageUsers(page, size, keyword);
        return Result.success(PageData.of(p));
    }

    @Operation(summary = "封禁用户")
    @PostMapping("/{id}/ban")
    public Result<Void> ban(@PathVariable("id") Long id) {
        adminUserService.ban(id);
        return Result.success(null, "封禁成功");
    }

    @Operation(summary = "解封用户")
    @PostMapping("/{id}/unban")
    public Result<Void> unban(@PathVariable("id") Long id) {
        adminUserService.unban(id);
        return Result.success(null, "解封成功");
    }
}
