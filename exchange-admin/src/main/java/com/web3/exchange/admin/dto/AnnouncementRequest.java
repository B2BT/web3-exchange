package com.web3.exchange.admin.dto;

import lombok.Data;

/**
 * 公告创建/编辑请求。
 */
@Data
public class AnnouncementRequest {
    /** 公告ID(编辑时必填) */
    private Long id;
    /** 标题 */
    private String title;
    /** 内容 */
    private String content;
    /** 类型:0=公告,1=活动,2=系统 */
    private Integer type;
}
