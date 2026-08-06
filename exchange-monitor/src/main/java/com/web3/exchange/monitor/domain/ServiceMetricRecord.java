package com.web3.exchange.monitor.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单个服务实例的简化指标快照。
 * <p>
 * 目标服务未暴露 actuator 的 metrics 端点，故采用简化采集：以 Nacos 实例注册元数据
 * （ip/端口/权重/启停/心跳健康/自定义元数据）为主，叠加探测响应耗时与当前健康状态。
 * 若目标服务恰好暴露 /actuator/metrics/jvm.memory.used，则额外抓取堆内存用量。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "服务实例指标快照")
public class ServiceMetricRecord {

    @Schema(description = "服务名", example = "exchange-asset")
    private String serviceName;

    @Schema(description = "实例地址 host:port")
    private String instance;

    @Schema(description = "实例 IP")
    private String ip;

    @Schema(description = "实例端口")
    private Integer port;

    @Schema(description = "健康状态 UP/DOWN")
    private String status;

    @Schema(description = "采集时间（毫秒时间戳）")
    private Long collectedAt;

    @Schema(description = "最近探测响应耗时（毫秒）")
    private Long responseTimeMs;

    @Schema(description = "指标明细（Nacos 元数据 + 可选 JVM 内存等）")
    private Map<String, Object> metrics;
}
