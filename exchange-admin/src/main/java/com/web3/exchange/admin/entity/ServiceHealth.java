package com.web3.exchange.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 服务健康快照表（t_service_health）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_service_health")
public class ServiceHealth extends BaseEntity {
    /** 服务名 */
    private String serviceName;
    /** 实例IP */
    private String instanceIp;
    /** 端口 */
    private Integer port;
    /** 状态:0=DOWN,1=UP */
    private Integer status;
    /** 已用内存(字节) */
    private Long memoryUsed;
    /** 总内存(字节) */
    private Long memoryTotal;
    /** 最近心跳 */
    private LocalDateTime lastHeartbeat;
}
