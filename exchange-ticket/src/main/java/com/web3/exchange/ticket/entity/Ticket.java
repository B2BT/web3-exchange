package com.web3.exchange.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客服工单（t_ticket）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ticket")
public class Ticket extends BaseEntity {
    /** 提交用户ID */
    private Long userId;
    /** 分类：DEPOSIT/WITHDRAW/TRADE/ACCOUNT/OTHER */
    private String category;
    /** 标题 */
    private String title;
    /** 问题描述 */
    private String content;
    /** 状态：0开放 1处理中 2已解决 3已关闭 */
    private Integer status;
    /** 优先级：0低 1中 2高 */
    private Integer priority;
    /** 处理管理员ID */
    private Long assigneeId;
    /** 解决时间 */
    private LocalDateTime resolvedAt;
}
