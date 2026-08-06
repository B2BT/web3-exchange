package com.web3.exchange.monitor.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个服务实例的健康快照。
 * <p>
 * 健康判定规则（针对未暴露 actuator 的目标服务，探活+注册中心状态双因子）：
 * <ul>
 *   <li>Nacos 心跳健康且实例已启用 → 注册中心层面 UP；</li>
 *   <li>HTTP 探测（GET /actuator/health，或回退根路径）能收到任何响应 → 网络层面可达 UP；</li>
 *   <li>两者任一 UP 判为整体 UP（本环境目标服务未暴露 actuator，探测返回 500 但可达）。</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "服务实例健康快照")
public class ServiceHealthRecord {

    @Schema(description = "服务名", example = "exchange-asset")
    private String serviceName;

    @Schema(description = "实例地址 host:port", example = "192.168.8.212:8103")
    private String instance;

    @Schema(description = "实例 IP")
    private String ip;

    @Schema(description = "实例端口")
    private Integer port;

    @Schema(description = "健康状态 UP/DOWN")
    private String status;

    @Schema(description = "Nacos 注册健康状态")
    private Boolean nacosHealthy;

    @Schema(description = "Nacos 实例是否启用")
    private Boolean nacosEnabled;

    @Schema(description = "HTTP 探测是否可达")
    private Boolean probeReachable;

    @Schema(description = "探测 HTTP 状态码，-1 表示不可达/异常")
    private Integer probeHttpStatus;

    @Schema(description = "最近检查时间（毫秒时间戳）")
    private Long checkedAt;

    @Schema(description = "响应耗时（毫秒）")
    private Long responseTimeMs;

    @Schema(description = "探测描述（错误信息或 actuator 状态）")
    private String detail;
}
