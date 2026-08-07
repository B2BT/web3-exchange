package com.web3.exchange.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新工单状态请求。
 */
@Data
public class TicketStatusDTO {
    @NotNull(message = "工单ID不能为空")
    private Long ticketId;
    /** 目标状态：0开放 1处理中 2已解决 3已关闭 */
    @NotNull(message = "状态不能为空")
    private Integer status;
    /** 处理管理员ID */
    private Long assigneeId;
}
