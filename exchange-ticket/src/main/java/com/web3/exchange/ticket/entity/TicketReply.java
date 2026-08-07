package com.web3.exchange.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单回复（t_ticket_reply）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ticket_reply")
public class TicketReply extends BaseEntity {
    /** 工单ID */
    private Long ticketId;
    /** 回复人ID */
    private Long userId;
    /** 1=管理员 0=用户 */
    private Integer isStaff;
    /** 回复内容 */
    private String content;
}
