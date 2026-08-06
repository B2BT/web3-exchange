package com.web3.exchange.notify.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.notify.service.NotificationService;
import com.web3.exchange.notify.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知<b>对外</b> REST 接口（/api/notify/**）——经网关转发。
 * <p>
 * userId 本期作为请求参数传入（尚未接统一鉴权取当前用户）；标记已读接口校验通知归属，
 * 越权返回 Result.error。
 * </p>
 */
@RestController
@RequestMapping("/api/notify")
@Tag(name = "站内通知接口")
public class NotifyController {

    private final NotificationService notificationService;

    public NotifyController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 分页查询用户通知（按 create_time 倒序），支持按已读状态过滤。
     */
    @Operation(summary = "分页查询用户通知")
    @GetMapping("/list")
    public Result<Page<NotificationVO>> list(@RequestParam("userId") Long userId,
                                             @RequestParam(value = "isRead", required = false) Integer isRead,
                                             @RequestParam(value = "page", defaultValue = "1") int page,
                                             @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(notificationService.pageByUser(userId, isRead, page, size));
    }

    /**
     * 统计用户未读通知数。
     */
    @Operation(summary = "统计用户未读通知数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@RequestParam("userId") Long userId) {
        return Result.success(notificationService.unreadCount(userId));
    }

    /**
     * 标记单条已读（校验归属 user_id==userId）。
     */
    @Operation(summary = "标记单条已读")
    @PostMapping("/{id}/read")
    public Result<Boolean> markRead(@PathVariable("id") Long id,
                                    @RequestParam("userId") Long userId) {
        boolean ok = notificationService.markRead(id, userId);
        if (!ok) {
            return Result.error(404, "通知不存在或不属于该用户");
        }
        return Result.success(true);
    }

    /**
     * 将该用户全部未读通知置为已读，返回更新条数。
     */
    @Operation(summary = "标记全部已读")
    @PostMapping("/read-all")
    public Result<Integer> readAll(@RequestParam("userId") Long userId) {
        return Result.success(notificationService.markAllRead(userId));
    }
}
