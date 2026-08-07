package com.web3.exchange.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交工单请求。
 */
@Data
public class TicketCreateDTO {
    /** 分类：DEPOSIT/WITHDRAW/TRADE/ACCOUNT/OTHER */
    @NotBlank(message = "分类不能为空")
    private String category;
    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题过长")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;
    /** 优先级：0低 1中 2高 */
    private Integer priority;
}
