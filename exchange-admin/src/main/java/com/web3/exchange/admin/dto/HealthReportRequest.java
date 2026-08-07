package com.web3.exchange.admin.dto;

import lombok.Data;

/**
 * 服务健康上报请求（各服务 Feign 调用 /internal/admin/health/report）。
 */
@Data
public class HealthReportRequest {
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
}
