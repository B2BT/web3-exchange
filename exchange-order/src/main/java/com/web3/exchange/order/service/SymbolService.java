package com.web3.exchange.order.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.web3.exchange.common.exception.ServiceException;
import com.web3.exchange.order.entity.Symbol;
import com.web3.exchange.order.mapper.SymbolMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 交易对服务：交易中交易对的配置查询与校验。
 * <p><b>Caffeine 本地缓存</b>：交易对配置低频变化，用本地缓存减少下单高频路径的 DB 查询。
 * 缓存 5 分钟 + 最大 1000 条，过期自动回源 DB。</p>
 */
@Slf4j
@Service
public class SymbolService {

    private final SymbolMapper symbolMapper;

    /** 交易对配置本地缓存（读多写少） */
    private final Cache<String, Symbol> cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public SymbolService(SymbolMapper symbolMapper) {
        this.symbolMapper = symbolMapper;
    }

    /**
     * 获取交易中（status=1）的交易对；不存在或停牌抛业务异常。
     * 命中缓存则免 DB 查询。
     */
    public Symbol requireActive(String symbol) {
        Symbol cached = cache.getIfPresent(symbol);
        if (cached != null) {
            return cached;
        }
        Symbol s = symbolMapper.selectActiveBySymbol(symbol);
        if (s == null) {
            throw new ServiceException("交易对不存在或已停牌: " + symbol);
        }
        cache.put(symbol, s);
        return s;
    }

    /** 失效某交易对缓存（交易对状态变更时调用，保证强一致）。 */
    public void evict(String symbol) {
        cache.invalidate(symbol);
    }
}
