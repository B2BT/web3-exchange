package com.web3.exchange.monitor.service;

import com.web3.exchange.monitor.config.MonitorProperties;
import com.web3.exchange.monitor.config.NacosClient;
import com.web3.exchange.monitor.domain.ServiceMetricRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标采集服务（简化版）。
 * <p>
 * 定时从 Nacos 拉取各服务实例，采集简化指标（实例注册元数据 + 探测耗时 + 可选 JVM 堆内存）。
 * 快照以内存 {@link ConcurrentHashMap} 保存，通过 /api/monitor/metrics 查询。
 * </p>
 */
@Slf4j
@Service
public class MetricService {

    private final DiscoveryClient discoveryClient;
    private final NacosClient nacosClient;
    private final RestTemplate restTemplate;
    private final MonitorProperties props;
    private final HealthMonitorService healthMonitorService;

    /** 实例级指标快照：key = serviceName:ip:port */
    private final Map<String, ServiceMetricRecord> snapshot = new ConcurrentHashMap<>();

    public MetricService(DiscoveryClient discoveryClient,
                         NacosClient nacosClient,
                         RestTemplate restTemplate,
                         MonitorProperties props,
                         HealthMonitorService healthMonitorService) {
        this.discoveryClient = discoveryClient;
        this.nacosClient = nacosClient;
        this.restTemplate = restTemplate;
        this.props = props;
        this.healthMonitorService = healthMonitorService;
    }

    @Scheduled(fixedDelayString = "${monitor.metrics-interval-ms:60000}", initialDelay = 6000)
    public void scheduledCollectMetrics() {
        collectMetrics();
    }

    /**
     * 采集一轮简化指标（与健康检查共享实例判定，避免重复探测）。
     */
    public void collectMetrics() {
        List<String> services = resolveServices();
        for (String service : services) {
            Map<String, NacosClient.NacosInstance> instances = nacosClient.listInstances(service);
            for (NacosClient.NacosInstance i : instances.values()) {
                String address = i.ip + ":" + i.port;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nacos.healthy", i.healthy);
                m.put("nacos.enabled", i.enabled);
                m.put("nacos.weight", i.weight);
                if (!i.metadata.isEmpty()) {
                    m.put("metadata", i.metadata);
                }
                // 目标服务暴露 actuator metrics 时抓取 JVM 堆内存（否则跳过，保持容错）
                Long jvmUsed = tryFetchJvmMemoryUsed(address);
                if (jvmUsed != null) {
                    m.put("jvm.memory.used.bytes", jvmUsed);
                }
                // 复用健康快照的状态与耗时
                var health = healthMonitorService.listHealth(service).stream()
                        .filter(r -> address.equals(r.getInstance()))
                        .findFirst().orElse(null);
                ServiceMetricRecord rec = ServiceMetricRecord.builder()
                        .serviceName(service)
                        .instance(address)
                        .ip(i.ip)
                        .port(i.port)
                        .status(health != null ? health.getStatus() : (i.healthy ? "UP" : "DOWN"))
                        .collectedAt(System.currentTimeMillis())
                        .responseTimeMs(health != null ? health.getResponseTimeMs() : 0L)
                        .metrics(m)
                        .build();
                snapshot.put(service + ":" + address, rec);
            }
        }
        log.info("指标采集完成，实例快照数={}", snapshot.size());
    }

    private Long tryFetchJvmMemoryUsed(String address) {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(
                    "http://" + address + "/actuator/metrics/jvm.memory.used", String.class);
            String body = resp.getBody();
            if (body != null && resp.getStatusCode().is2xxSuccessful() && body.contains("value")) {
                int idx = body.indexOf("\"value\"");
                if (idx >= 0) {
                    String num = body.substring(idx + 8).trim();
                    int end = num.indexOf('}');
                    if (end > 0) num = num.substring(0, end);
                    num = num.replaceAll("[^0-9.\\-]", "");
                    return (long) Double.parseDouble(num);
                }
            }
        } catch (RestClientException | NumberFormatException ignored) {
            // 目标未暴露 actuator metrics → 忽略
        }
        return null;
    }

    private List<String> resolveServices() {
        if (props.getServices() != null && !props.getServices().isEmpty()) {
            return props.getServices();
        }
        List<String> out = new ArrayList<>();
        try {
            for (String s : discoveryClient.getServices()) {
                if (s.startsWith("exchange-")) out.add(s);
            }
        } catch (Exception e) {
            log.warn("发现服务列表失败：{}", e.getMessage());
        }
        return out;
    }

    /** 查询全部指标快照（按服务+实例排序）。 */
    public List<ServiceMetricRecord> listMetrics() {
        return snapshot.values().stream()
                .sorted(Comparator.comparing(ServiceMetricRecord::getServiceName)
                        .thenComparing(ServiceMetricRecord::getInstance))
                .toList();
    }

    /** 查询单服务指标快照。 */
    public List<ServiceMetricRecord> listMetrics(String service) {
        return snapshot.values().stream()
                .filter(r -> service.equals(r.getServiceName()))
                .sorted(Comparator.comparing(ServiceMetricRecord::getInstance))
                .toList();
    }
}
