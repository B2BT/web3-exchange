package com.web3.exchange.ticket.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单视图。
 */
@Data
public class TicketVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String category;
    private String title;
    private String content;
    private Integer status;
    private Integer priority;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assigneeId;
    private LocalDateTime resolvedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
