package com.web3.exchange.chain.config;

import com.web3.exchange.chain.entity.Chain;
import com.web3.exchange.chain.registry.ChainRegistry;
import com.web3.exchange.chain.service.ChainService;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * web3j 配置：按启用链构造 Web3j 实例（HttpService 指向 t_chain.rpc_url）并注册到 ChainRegistry。
 * <p>不用 web3j-spring-boot-starter（面向 Spring Boot 2），采用 core + 手动配置，可多链多实例。</p>
 */
@Configuration
public class Web3jConfig {

    /**
     * 每启用链建一个 Web3j（HttpService 指向 t_chain.rpc_url），注册 ChainRegistry。
     */
    @Bean
    public ChainRegistry chainRegistry(ChainService chainService) {
        Map<String, Web3j> map = new HashMap<>();
        List<Chain> chains = chainService.listEnabled();
        for (Chain c : chains) {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                HttpService hs = new HttpService(c.getRpcUrl(), client, false);
                map.put(c.getChainCode(), Web3j.build(hs));
                // 预热：校验 RPC 可达并打印 chainId（便于 Mock 阶段排查）
                try {
                    var chainId = Web3j.build(hs).ethChainId().send();
                } catch (Exception ignored) {
                    // RPC 暂不可达不阻断启动，扫描时再报
                }
            } catch (Exception e) {
                // 单链构造失败不阻断其他链
            }
        }
        return new ChainRegistry(map);
    }
}
