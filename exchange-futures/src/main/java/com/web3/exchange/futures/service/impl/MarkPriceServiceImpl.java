package com.web3.exchange.futures.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.futures.dto.SpotTickerVO;
import com.web3.exchange.futures.entity.MarkPrice;
import com.web3.exchange.futures.entity.SwapContract;
import com.web3.exchange.futures.feign.MarketClient;
import com.web3.exchange.futures.mapper.MarkPriceMapper;
import com.web3.exchange.futures.mapper.SwapContractMapper;
import com.web3.exchange.futures.service.MarkPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 标记价格服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarkPriceServiceImpl implements MarkPriceService {

    private final MarkPriceMapper markPriceMapper;
    private final SwapContractMapper contractMapper;
    private final MarketClient marketClient;

    /** 基差因子（现货价 × (1 + 因子) = 标记价），默认 0.0002 */
    @Value("${server-settings.mark.basis-factor:0.0002}")
    private double basisFactor;

    @Override
    public Long getMarkPrice(String symbol) {
        MarkPrice mp = markPriceMapper.selectOne(
                new LambdaQueryWrapper<MarkPrice>().eq(MarkPrice::getSymbol, symbol).last("LIMIT 1"));
        return mp == null ? null : mp.getMarkPrice();
    }

    /**
     * 刷新所有上架合约的标记价：现货价 × (1 + 基差)。
     * 返回更新的交易对数。
     */
    public int refreshAll() {
        var contracts = contractMapper.selectList(
                new LambdaQueryWrapper<SwapContract>().eq(SwapContract::getStatus, 0));
        int updated = 0;
        for (SwapContract c : contracts) {
            Long spotPrice = fetchSpotPrice(c);
            if (spotPrice == null) {
                continue;
            }
            // 标记价 = spot × (1 + basis)，basis 放大 1e6（PPM）
            BigDecimal spot = BigDecimal.valueOf(spotPrice);
            BigDecimal factor = BigDecimal.valueOf(1 + basisFactor);
            long mark = spot.multiply(factor).setScale(0, RoundingMode.HALF_UP).longValue();
            saveMarkPrice(c.getSymbol(), mark);
            updated++;
        }
        return updated;
    }

    /** 从现货行情拉价格。 */
    private Long fetchSpotPrice(SwapContract c) {
        try {
            String base = c.getBase();
            String quote = c.getQuote();
            String spotSymbol = base + "/" + quote;
            Result<SpotTickerVO> res = marketClient.ticker(spotSymbol);
            if (res == null || res.getData() == null || res.getData().getLastPrice() == null) {
                return null;
            }
            return new BigDecimal(res.getData().getLastPrice()).longValue();
        } catch (Exception e) {
            log.warn("拉取现货价失败 symbol={}: {}", c.getSymbol(), e.getMessage());
            return null;
        }
    }

    private void saveMarkPrice(String symbol, long mark) {
        MarkPrice exist = markPriceMapper.selectOne(
                new LambdaQueryWrapper<MarkPrice>().eq(MarkPrice::getSymbol, symbol).last("LIMIT 1"));
        if (exist == null) {
            MarkPrice mp = new MarkPrice();
            mp.setSymbol(symbol);
            mp.setMarkPrice(mark);
            markPriceMapper.insert(mp);
        } else {
            exist.setMarkPrice(mark);
            markPriceMapper.updateById(exist);
        }
    }
}
