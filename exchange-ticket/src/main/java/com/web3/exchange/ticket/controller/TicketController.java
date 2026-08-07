package com.web3.exchange.ticket.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.common.model.PageData;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.ticket.dto.TicketCreateDTO;
import com.web3.exchange.ticket.dto.TicketReplyDTO;
import com.web3.exchange.ticket.service.TicketService;
import com.web3.exchange.ticket.vo.TicketDetailVO;
import com.web3.exchange.ticket.vo.TicketReplyVO;
import com.web3.exchange.ticket.vo.TicketVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 用户侧工单接口（/api/ticket/**，JWT 鉴权）。
 */
@RestController
@RequestMapping("/api/ticket")
@Tag(name = "客服工单(用户)", description = "用户提交/查看/跟进工单")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "提交工单")
    @PostMapping("/create")
    public Result<TicketVO> create(@RequestHeader("X-User-Id") Long userId,
                                   @Valid @RequestBody TicketCreateDTO dto) {
        return Result.success(ticketService.create(userId, dto));
    }

    @Operation(summary = "我的工单分页")
    @GetMapping("/list")
    public Result<PageData<TicketVO>> list(@RequestHeader("X-User-Id") Long userId,
                                           @RequestParam(value = "page", defaultValue = "1") int page,
                                           @RequestParam(value = "size", defaultValue = "20") int size,
                                           @RequestParam(value = "status", required = false) Integer status) {
        return Result.success(PageData.of(ticketService.pageMine(userId, page, size, status)));
    }

    @Operation(summary = "工单详情+回复")
    @GetMapping("/{id}")
    public Result<TicketDetailVO> detail(@RequestHeader("X-User-Id") Long userId,
                                         @PathVariable("id") Long id) {
        return Result.success(ticketService.detail(userId, id));
    }

    @Operation(summary = "追加工单回复")
    @PostMapping("/reply")
    public Result<TicketReplyVO> reply(@RequestHeader("X-User-Id") Long userId,
                                       @Valid @RequestBody TicketReplyDTO dto) {
        return Result.success(ticketService.replyUser(userId, dto));
    }

    @Operation(summary = "关闭工单")
    @PostMapping("/{id}/close")
    public Result<Void> close(@RequestHeader("X-User-Id") Long userId,
                              @PathVariable("id") Long id) {
        ticketService.close(userId, id);
        return Result.success();
    }
}
