package com.web3.exchange.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.dto.AnnouncementRequest;
import com.web3.exchange.admin.dto.SymbolRequest;
import com.web3.exchange.admin.entity.AdminAudit;
import com.web3.exchange.admin.entity.AdminSymbol;
import com.web3.exchange.admin.entity.Announcement;
import com.web3.exchange.admin.entity.ServiceHealth;
import com.web3.exchange.admin.service.AdminBService;
import com.web3.exchange.common.model.PageData;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin B 后台接口（/api/admin/**，需 ADMIN 角色，拦截器已鉴权）。
 * <p>公告管理、审计日志、服务监控、交易对管理。</p>
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin B 后台增强")
public class AdminBController {

    private final AdminBService adminBService;

    public AdminBController(AdminBService adminBService) {
        this.adminBService = adminBService;
    }

    // ==================== 公告 ====================

    @Operation(summary = "公告分页")
    @GetMapping("/announcement/list")
    public Result<PageData<Announcement>> announcements(@RequestParam(value = "page", defaultValue = "1") int page,
                                                        @RequestParam(value = "size", defaultValue = "20") int size,
                                                        @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(PageData.of(adminBService.pageAnnouncements(page, size, keyword)));
    }

    @Operation(summary = "新建公告")
    @PostMapping("/announcement/create")
    public Result<Announcement> createAnnouncement(@Valid @RequestBody AnnouncementRequest req,
                                                   @RequestHeader("X-User-Id") Long adminId) {
        return Result.success(adminBService.createAnnouncement(req, adminId));
    }

    @Operation(summary = "编辑公告")
    @PostMapping("/announcement/update")
    public Result<Announcement> updateAnnouncement(@Valid @RequestBody AnnouncementRequest req,
                                                   @RequestHeader("X-User-Id") Long adminId) {
        return Result.success(adminBService.updateAnnouncement(req, adminId));
    }

    @Operation(summary = "发布/下线公告")
    @PostMapping("/announcement/{id}/publish")
    public Result<Announcement> publishAnnouncement(@PathVariable("id") Long id,
                                                    @RequestParam("publish") boolean publish,
                                                    @RequestHeader("X-User-Id") Long adminId) {
        return Result.success(adminBService.publishAnnouncement(id, publish, adminId));
    }

    @Operation(summary = "删除公告")
    @PostMapping("/announcement/{id}/delete")
    public Result<Void> deleteAnnouncement(@PathVariable("id") Long id,
                                           @RequestHeader("X-User-Id") Long adminId) {
        adminBService.deleteAnnouncement(id, adminId);
        return Result.success(null, "删除成功");
    }

    // ==================== 审计 ====================

    @Operation(summary = "审计日志分页")
    @GetMapping("/audit/list")
    public Result<PageData<AdminAudit>> audits(@RequestParam(value = "page", defaultValue = "1") int page,
                                               @RequestParam(value = "size", defaultValue = "20") int size,
                                               @RequestParam(value = "adminUserId", required = false) Long adminUserId) {
        return Result.success(PageData.of(adminBService.pageAudits(page, size, adminUserId)));
    }

    // ==================== 服务监控 ====================

    @Operation(summary = "服务健康列表")
    @GetMapping("/health/list")
    public Result<PageData<ServiceHealth>> health(@RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "size", defaultValue = "50") int size) {
        return Result.success(PageData.of(adminBService.pageHealth(page, size)));
    }

    // ==================== 交易对 ====================

    @Operation(summary = "交易对分页")
    @GetMapping("/symbol/list")
    public Result<PageData<AdminSymbol>> symbols(@RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(PageData.of(adminBService.pageSymbols(page, size, keyword)));
    }

    @Operation(summary = "新增交易对")
    @PostMapping("/symbol/create")
    public Result<AdminSymbol> createSymbol(@Valid @RequestBody SymbolRequest req,
                                            @RequestHeader("X-User-Id") Long adminId) {
        return Result.success(adminBService.createSymbol(req, adminId));
    }

    @Operation(summary = "编辑交易对")
    @PostMapping("/symbol/update")
    public Result<AdminSymbol> updateSymbol(@Valid @RequestBody SymbolRequest req,
                                            @RequestHeader("X-User-Id") Long adminId) {
        return Result.success(adminBService.updateSymbol(req, adminId));
    }

    @Operation(summary = "上牌/停牌")
    @PostMapping("/symbol/{id}/toggle")
    public Result<AdminSymbol> toggleSymbol(@PathVariable("id") Long id,
                                            @RequestParam("trading") boolean trading,
                                            @RequestHeader("X-User-Id") Long adminId) {
        return Result.success(adminBService.toggleSymbol(id, trading, adminId));
    }

    @Operation(summary = "当前管理员信息(前端展示用)")
    @GetMapping("/whoami")
    public Result<String> whoami(HttpServletRequest request) {
        return Result.success(request.getHeader("X-User-Id"));
    }
}
