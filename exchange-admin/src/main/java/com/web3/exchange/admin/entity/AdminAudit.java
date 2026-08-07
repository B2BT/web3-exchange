package com.web3.exchange.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 管理员审计日志表（t_admin_audit）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_admin_audit")
public class AdminAudit extends BaseEntity {
    /** 管理员用户ID */
    private Long adminUserId;
    /** 管理员用户名 */
    private String adminUsername;
    /** 操作类型 */
    private String action;
    /** 目标类型 */
    private String targetType;
    /** 目标ID */
    private String targetId;
    /** 详情 */
    private String detail;
    /** IP */
    private String ip;
}
