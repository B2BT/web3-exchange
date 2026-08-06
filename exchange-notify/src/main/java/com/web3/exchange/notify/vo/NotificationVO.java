package com.web3.exchange.notify.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知视图（对外返回）。
 * <p>金额字段为 Long 最小单位原始值，展示层换算。isRead: 0=未读 1=已读。</p>
 */
@Data
@Schema(description = "站内通知视图")
public class NotificationVO implements Serializable {

    @Schema(description = "通知ID")
    private Long id;

    @Schema(description = "接收用户ID")
    private Long userId;

    @Schema(description = "通知类型:DEPOSIT_CONFIRMED/WITHDRAW_SUCCESS/TRADE_FILLED")
    private String type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "关联业务单号")
    private String bizRef;

    @Schema(description = "关联币种/交易对")
    private String symbol;

    @Schema(description = "关联金额(最小单位)")
    private Long amount;

    @Schema(description = "已读状态:0=未读 1=已读")
    private Integer isRead;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
