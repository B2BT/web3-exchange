package com.web3.exchange.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 公告表（t_announcement）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_announcement")
public class Announcement extends BaseEntity {
    /** 标题 */
    private String title;
    /** 内容 */
    private String content;
    /** 类型:0=公告,1=活动,2=系统 */
    private Integer type;
    /** 状态:0=草稿,1=已发布,2=已下线 */
    private Integer status;
    /** 发布时间 */
    private LocalDateTime publishTime;
    /** 发布人ID */
    private Long publisherId;
    /** 浏览量 */
    private Integer viewCount;
}
