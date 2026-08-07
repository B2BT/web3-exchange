package com.web3.exchange.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工单回复请求。
 */
@Data
public class TicketReplyDTO {
    @NotNull(message = "工单ID不能为空")
    private Long ticketId;
    @NotBlank(message = "回复内容不能为空")
    private String content;
}
