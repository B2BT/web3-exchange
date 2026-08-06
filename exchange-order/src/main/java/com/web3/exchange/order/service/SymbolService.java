package com.web3.exchange.order.service;

import com.web3.exchange.common.exception.ServiceException;
import com.web3.exchange.order.entity.Symbol;
import com.web3.exchange.order.mapper.SymbolMapper;
import org.springframework.stereotype.Service;

/**
 * 交易对服务：交易中交易对的配置查询与校验。
 */
@Service
public class SymbolService {

    private final SymbolMapper symbolMapper;

    public SymbolService(SymbolMapper symbolMapper) {
        this.symbolMapper = symbolMapper;
    }

    /**
     * 获取交易中（status=1）的交易对；不存在或停牌抛业务异常。
     */
    public Symbol requireActive(String symbol) {
        Symbol s = symbolMapper.selectActiveBySymbol(symbol);
        if (s == null) {
            throw new ServiceException("交易对不存在或已停牌: " + symbol);
        }
        return s;
    }
}
