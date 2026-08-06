package com.web3.exchange.monitor.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.monitor.domain.ServiceHealthRecord;
import com.web3.exchange.monitor.domain.ServiceMetricRecord;
import com.web3.exchange.monitor.service.HealthMonitorService;
import com.web3.exchange.monitor.service.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 监控域对外 REST 接口（/api/monitor/**）。
 * <p>
 * 提供全服务/单服务健康查询，以及按服务名的简化指标查询。
 * </p>
 */
@RestController
@RequestMapping("/api/monitor")
@Tag(name = "监控接口")
public class MonitorController {

    private final HealthMonitorService healthMonitorService;
    private final MetricService metricService;

    public MonitorController(HealthMonitorService healthMonitorService, MetricService metricService) {
        this.healthMonitorService = healthMonitorService;
        this.metricService = metricService;
    }

    @Operation(summary = "全服务健康状态")
    @GetMapping("/health/list")
    public Result<List<ServiceHealthRecord>> healthList() {
        return Result.success(healthMonitorService.listHealth());
    }

    @Operation(summary = "单服务健康状态")
    @GetMapping("/health/{service}")
    public Result<List<ServiceHealthRecord>> healthByService(@PathVariable("service") String service) {
        List<ServiceHealthRecord> list = healthMonitorService.listHealth(service);
        if (list.isEmpty()) {
            return Result.notFound("未找到服务: " + service + " 的健康记录");
        }
        return Result.success(list);
    }

    @Operation(summary = "全服务指标快照")
    @GetMapping("/metrics/list")
    public Result<List<ServiceMetricRecord>> metricsList() {
        return Result.success(metricService.listMetrics());
    }

    @Operation(summary = "单服务指标快照")
    @GetMapping("/metrics")
    public Result<List<ServiceMetricRecord>> metricsByService(@RequestParam("service") String service) {
        List<ServiceMetricRecord> list = metricService.listMetrics(service);
        if (list.isEmpty()) {
            return Result.notFound("未找到服务: " + service + " 的指标记录");
        }
        return Result.success(list);
    }

    @Operation(summary = "手动触发一轮健康检查")
    @GetMapping("/health/trigger")
    public Result<String> triggerHealth() {
        healthMonitorService.collectHealth();
        return Result.success("健康检查已触发");
    }
}
