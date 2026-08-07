package com.web3.exchange.ticket.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.common.model.PageData;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.ticket.dto.TicketReplyDTO;
import com.web3.exchange.ticket.dto.TicketStatusDTO;
import com.web3.exchange.ticket.service.TicketService;
import com.web3.exchange.ticket.vo.TicketReplyVO;
import com.web3.exchange.ticket.vo.TicketVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 管理侧工单接口（/api/admin/ticket/**，需 ADMIN 角色，网关下 exchange-admin 网关路由会转发；此处为 exchange-ticket 自身暴露的管理接口，
 * 由网关路由 /api/admin/ticket/** 到本服务）。
 */
@RestController
@RequestMapping("/api/admin/ticket")
@Tag(name = "客服工单(管理)", description = "管理员查看/回复/流转工单")
public class AdminTicketController {

    private final TicketService ticketService;

    public AdminTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "全部工单分页")
    @GetMapping("/list")
    public Result<PageData<TicketVO>> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                           @RequestParam(value = "size", defaultValue = "20") int size,
                                           @RequestParam(value = "status", required = false) Integer status) {
        return Result.success(PageData.of(ticketService.pageAll(page, size, status)));
    }

    @Operation(summary = "管理员回复")
    @PostMapping("/reply")
    public Result<TicketReplyVO> reply(@RequestHeader("X-User-Id") Long adminId,
                                       @Valid @RequestBody TicketReplyDTO dto) {
        return Result.success(ticketService.replyStaff(adminId, dto));
    }

    @Operation(summary = "更新工单状态")
    @PostMapping("/status")
    public Result<Void> status(@RequestHeader("X-User-Id") Long adminId,
                               @Valid @RequestBody TicketStatusDTO dto) {
        ticketService.updateStatus(adminId, dto);
        return Result.success();
    }
}
