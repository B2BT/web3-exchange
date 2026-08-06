package com.web3.exchange.monitor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Nacos Open API 轻量客户端。
 * <p>
 * 通过 Nacos 开放 HTTP API（/nacos/v1/ns/instance/list）拉取某服务的全部实例及其
 * 健康/启用/权重/元数据，避免依赖 spring-cloud-alibaba 的 {@code NamingService} Bean
 * （该 Bean 在本环境未自动装配）。
 * </p>
 */
@Slf4j
@Component
public class NacosClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String serverAddr;

    public NacosClient(@Value("${spring.cloud.nacos.server-addr:127.0.0.1:8848}") String serverAddr,
                       RestTemplate restTemplate,
                       ObjectMapper objectMapper) {
        this.serverAddr = serverAddr;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 拉取某服务全部实例，host:port 为 key。
     */
    public Map<String, NacosInstance> listInstances(String service) {
        Map<String, NacosInstance> map = new HashMap<>();
        String url = "http://" + serverAddr + "/nacos/v1/ns/instance/list?serviceName=" + service;
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode hosts = root.get("hosts");
            if (hosts != null && hosts.isArray()) {
                for (JsonNode h : hosts) {
                    NacosInstance ni = new NacosInstance();
                    ni.ip = h.path("ip").asText();
                    ni.port = h.path("port").asInt();
                    ni.healthy = h.path("healthy").asBoolean(false);
                    ni.enabled = h.path("enabled").asBoolean(true);
                    ni.weight = h.path("weight").asDouble(1.0);
                    JsonNode md = h.get("metadata");
                    if (md != null && md.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> it = md.fields();
                        while (it.hasNext()) {
                            Map.Entry<String, JsonNode> e = it.next();
                            ni.metadata.put(e.getKey(), e.getValue().asText());
                        }
                    }
                    map.put(ni.ip + ":" + ni.port, ni);
                }
            }
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Nacos 拉取实例失败 service={} err={}", service, e.getMessage());
        }
        return map;
    }

    /** Nacos 实例信息值对象。 */
    public static class NacosInstance {
        public String ip;
        public int port;
        public boolean healthy;
        public boolean enabled = true;
        public double weight = 1.0;
        public final Map<String, String> metadata = new HashMap<>();
    }
}
