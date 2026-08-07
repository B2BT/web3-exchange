package com.web3.exchange.ticket.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 工单详情视图（工单 + 回复列表）。
 */
@Data
public class TicketDetailVO {
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
    private java.time.LocalDateTime resolvedAt;
    private java.time.LocalDateTime createTime;
    private List<TicketReplyVO> replies;
}
