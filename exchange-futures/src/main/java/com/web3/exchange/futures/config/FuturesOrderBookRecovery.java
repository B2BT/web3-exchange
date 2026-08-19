package com.web3.exchange.futures.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.web3.exchange.futures.engine.FuturesMatchingEngine;
import com.web3.exchange.futures.entity.FuturesOrder;
import com.web3.exchange.futures.mapper.FuturesOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动重建期货订单簿（二梯队可用性：内存撮合簿重启丢失修复）。
 * <p>合约定单已落库（t_futures_order），重启时把 status IN (0,1) 且剩余数量&gt;0 的
 * 活跃限价单重新挂入内存撮合簿，保证撮合簿不因重启丢失（与现货 OrderBookRecovery 同模式）。
 * 市价单一次性撮合不落簿，无需恢复。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuturesOrderBookRecovery implements ApplicationListener<ApplicationReadyEvent> {

    private final FuturesOrderMapper orderMapper;
    private final FuturesMatchingEngine matchingEngine;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 状态语义：0=待成交(新单),1=部分成交；均为活跃挂单
        List<FuturesOrder> active = orderMapper.selectList(new LambdaQueryWrapper<FuturesOrder>()
                .in(FuturesOrder::getStatus, 0, 1)
                .gt(FuturesOrder::getRemaining, 0));
        if (active.isEmpty()) {
            log.info("[futures] 启动重建订单簿：无活跃挂单");
            return;
        }
        int recovered = 0;
        for (FuturesOrder o : active) {
            matchingEngine.restore(o.getSymbol(), o);
            recovered++;
        }
        log.info("[futures] 启动重建订单簿：恢复 {} 个活跃挂单（{} 个交易对）", recovered,
                active.stream().map(FuturesOrder::getSymbol).distinct().count());
    }
}
