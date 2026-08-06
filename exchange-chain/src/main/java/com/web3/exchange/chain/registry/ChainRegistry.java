package com.web3.exchange.chain.registry;

import org.web3j.protocol.Web3j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 链注册表：chainCode → Web3j 实例（每启用链一个，由 {@code Web3jConfig} 初始化）。
 * <p>支持多链多实例：BTC/TRON 本期不激活（chain_type=EVM 且 scan_enabled=1 才注册扫描），
 * 但通过本注册表为后续多链扩展预留。</p>
 */
public class ChainRegistry {

    private final Map<String, Web3j> providers;

    public ChainRegistry(Map<String, Web3j> providers) {
        this.providers = new HashMap<>(providers);
    }

    /**
     * 取某链的 Web3j 实例；不存在返回 null。
     */
    public Web3j get(String chainCode) {
        return providers.get(chainCode);
    }

    public boolean contains(String chainCode) {
        return providers.containsKey(chainCode);
    }

    public Map<String, Web3j> providers() {
        return Collections.unmodifiableMap(providers);
    }
}
