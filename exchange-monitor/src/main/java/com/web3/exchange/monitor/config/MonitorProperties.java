package com.web3.exchange.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 监控域配置项（前缀 monitor）。
 * <p>
 * 通过 nacos 服务发现 + 实例探测完成健康/指标采集，本配置项控制轮询间隔、
 * 探测超时与待监控服务列表（列表为空时自动发现 nacos 中全部 exchange-* 服务）。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "monitor")
public class MonitorProperties {

    /** 健康检查轮询间隔（毫秒）。 */
    private long healthIntervalMs = 30000L;

    /** 指标采集间隔（毫秒）。 */
    private long metricsIntervalMs = 60000L;

    /** 探测各实例时的 HTTP 超时（毫秒）。 */
    private int probeTimeoutMs = 3000;

    /** 需要监控的服务名，为空时自动发现 nacos 中全部 exchange-* 服务。 */
    private List<String> services = new ArrayList<>();
}
