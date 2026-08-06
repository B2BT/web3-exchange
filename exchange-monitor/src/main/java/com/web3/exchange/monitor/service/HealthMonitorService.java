package com.web3.exchange.monitor.service;

import com.web3.exchange.monitor.config.MonitorProperties;
import com.web3.exchange.monitor.config.NacosClient;
import com.web3.exchange.monitor.domain.ServiceHealthRecord;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务健康监控服务。
 * <p>
 * {@link Scheduled} 定时从 Nacos 拉取 exchange-* 服务实例列表，对每个实例做健康判定：
 * <ol>
 *   <li>读取 Nacos 实例心跳健康/启用状态（注册中心视角，经 Nacos Open API）；</li>
 *   <li>HTTP 探测 {@code GET /actuator/health}（目标服务未暴露 actuator 时回退探测根路径），
 *       收到任何 HTTP 响应即视为网络可达；</li>
 *   <li>任一维度 UP 即判整体 UP，并记录响应耗时。</li>
 * </ol>
 * 快照以内存 {@link ConcurrentHashMap} 保存，通过 REST 查询。
 * </p>
 */
@Slf4j
@Service
public class HealthMonitorService {

    private final DiscoveryClient discoveryClient;
    private final NacosClient nacosClient;
    private final RestTemplate restTemplate;
    private final MonitorProperties props;

    /** 实例级健康快照：key = serviceName:ip:port */
    private final Map<String, ServiceHealthRecord> snapshot = new ConcurrentHashMap<>();

    public HealthMonitorService(DiscoveryClient discoveryClient,
                                NacosClient nacosClient,
                                RestTemplate restTemplate,
                                MonitorProperties props) {
        this.discoveryClient = discoveryClient;
        this.nacosClient = nacosClient;
        this.restTemplate = restTemplate;
        this.props = props;
    }

    /** 启动后立即采集一次，保证查询接口无需等待首个调度周期即有数据。 */
    @PostConstruct
    public void init() {
        collectHealth();
    }

    @Scheduled(fixedDelayString = "${monitor.health-interval-ms:30000}", initialDelay = 3000)
    public void scheduledCollectHealth() {
        collectHealth();
    }

    /**
     * 采集一轮健康快照：发现服务 → 遍历实例 → 健康判定。
     */
    public void collectHealth() {
        long startedAt = System.currentTimeMillis();
        List<String> services = resolveServices();
        log.info("开始健康检查，服务数={}", services.size());
        int upCount = 0, downCount = 0;
        for (String service : services) {
            Map<String, NacosClient.NacosInstance> nacosInstances = nacosClient.listInstances(service);
            List<ServiceInstance> instances = discoveryClient.getInstances(service);
            if (instances.isEmpty()) {
                // 无实例但服务名在配置中 → 记录 DOWN
                ServiceHealthRecord rec = ServiceHealthRecord.builder()
                        .serviceName(service)
                        .instance("none")
                        .status("DOWN")
                        .nacosHealthy(false)
                        .nacosEnabled(false)
                        .probeReachable(false)
                        .probeHttpStatus(-1)
                        .checkedAt(System.currentTimeMillis())
                        .responseTimeMs(0L)
                        .detail("no registered instance")
                        .build();
                snapshot.put(key(service, "none", 0), rec);
                downCount++;
                continue;
            }
            for (ServiceInstance inst : instances) {
                ServiceHealthRecord rec = probeAndJudge(service, inst, nacosInstances);
                snapshot.put(key(service, inst.getHost(), inst.getPort()), rec);
                if ("UP".equals(rec.getStatus())) upCount++;
                else downCount++;
            }
        }
        log.info("健康检查完成，耗时={}ms，UP={}，DOWN={}", System.currentTimeMillis() - startedAt, upCount, downCount);
    }

    /**
     * 对单个实例做健康判定。
     */
    private ServiceHealthRecord probeAndJudge(String service, ServiceInstance inst,
                                              Map<String, NacosClient.NacosInstance> nacosInstances) {
        String host = inst.getHost();
        int port = inst.getPort();
        String address = host + ":" + port;

        NacosClient.NacosInstance nacosInst = nacosInstances.get(address);
        boolean nacosHealthy = nacosInst != null && nacosInst.healthy;
        boolean nacosEnabled = nacosInst == null || nacosInst.enabled;

        long t0 = System.currentTimeMillis();
        ProbeResult probe = probeHealth(address);
        long cost = System.currentTimeMillis() - t0;

        boolean reachable = probe.reachable;
        boolean up = nacosHealthy || reachable;
        String detail = probe.detail;
        if (nacosHealthy && reachable) {
            detail = "nacos healthy & probe reachable (" + detail + ")";
        } else if (nacosHealthy) {
            detail = "nacos healthy, probe unreachable";
        } else if (reachable) {
            detail = "nacos not healthy, but probe reachable (" + detail + ")";
        }

        ServiceHealthRecord rec = ServiceHealthRecord.builder()
                .serviceName(service)
                .instance(address)
                .ip(host)
                .port(port)
                .status(up ? "UP" : "DOWN")
                .nacosHealthy(nacosHealthy)
                .nacosEnabled(nacosEnabled)
                .probeReachable(reachable)
                .probeHttpStatus(probe.httpStatus)
                .checkedAt(System.currentTimeMillis())
                .responseTimeMs(cost)
                .detail(detail)
                .build();
        return rec;
    }

    /**
     * HTTP 探测：优先 /actuator/health，目标服务未暴露时回退到根路径。
     */
    private ProbeResult probeHealth(String address) {
        String base = "http://" + address;
        ProbeResult result = probeSingle(base + "/actuator/health");
        if (!result.reachable) {
            // 回退：根路径可达即证明服务在线
            ProbeResult fallback = probeSingle(base + "/");
            if (fallback.reachable) {
                return new ProbeResult(true, fallback.httpStatus, fallback.detail);
            }
            return new ProbeResult(false, -1, result.detail);
        }
        return result;
    }

    private ProbeResult probeSingle(String url) {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            String body = resp.getBody() == null ? "" : resp.getBody();
            String detail;
            if (resp.getStatusCode() == HttpStatus.OK && body.contains("UP")) {
                detail = "actuator UP";
            } else {
                detail = "http " + resp.getStatusCode().value();
            }
            return new ProbeResult(true, resp.getStatusCode().value(), detail);
        } catch (RestClientException e) {
            return new ProbeResult(false, -1, e.getClass().getSimpleName() + ": " + rootMessage(e));
        }
    }

    private String rootMessage(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null) c = c.getCause();
        String m = c.getMessage();
        return m == null ? c.getClass().getSimpleName() : m;
    }

    /**
     * 解析待监控服务列表：优先配置项，为空时自动发现 nacos 中全部 exchange-* 服务。
     */
    private List<String> resolveServices() {
        if (props.getServices() != null && !props.getServices().isEmpty()) {
            return props.getServices();
        }
        List<String> all = new ArrayList<>();
        try {
            for (String s : discoveryClient.getServices()) {
                if (s.startsWith("exchange-")) all.add(s);
            }
        } catch (Exception e) {
            log.warn("发现服务列表失败：{}", e.getMessage());
        }
        all.sort(String::compareTo);
        return all;
    }

    /** 查询全部服务健康快照（按服务名+实例排序）。 */
    public List<ServiceHealthRecord> listHealth() {
        return snapshot.values().stream()
                .sorted(Comparator.comparing(ServiceHealthRecord::getServiceName)
                        .thenComparing(ServiceHealthRecord::getInstance))
                .toList();
    }

    /** 查询单个服务健康快照（该服务全部实例）。 */
    public List<ServiceHealthRecord> listHealth(String service) {
        return snapshot.values().stream()
                .filter(r -> service.equals(r.getServiceName()))
                .sorted(Comparator.comparing(ServiceHealthRecord::getInstance))
                .toList();
    }

    private String key(String service, String ip, int port) {
        return service + ":" + ip + ":" + port;
    }

    /** 探测结果内部值对象。 */
    private record ProbeResult(boolean reachable, int httpStatus, String detail) {
    }
}
