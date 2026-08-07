package com.web3.exchange.ticket.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单回复视图。
 */
@Data
public class TicketReplyVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ticketId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private Integer isStaff;
    private String content;
    private LocalDateTime createTime;
}
