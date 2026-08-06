package com.web3.exchange.order.service;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.order.constant.OrderConstant;
import com.web3.exchange.order.dto.MarketTicker;
import com.web3.exchange.order.entity.Order;
import com.web3.exchange.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 条件单行情触发任务（docs/advanced-orders.md §三）。
 * <p>
 * 每 2s 轮询一次：从 market 服务拉取全市场最新价（GET /api/market/ticker/list），
 * 扫描 t_order 中 trigger_status=0 的条件单（复用 idx_trigger_status_symbol），
 * 按触发条件激活：止盈 lastPrice>=triggerPrice；止损 lastPrice<=triggerPrice。
 * 激活委托 {@link OrderService#activateConditional}（乐观锁防重复激活）。
 * </p>
 */
@Slf4j
@Component
public class TriggerOrderTask {

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final RestTemplate restTemplate;
    private final String tickerUrl;

    public TriggerOrderTask(OrderMapper orderMapper,
                            OrderService orderService,
                            RestTemplate orderRestTemplate,
                            @Value("${order.trigger.market-ticker-url:http://127.0.0.1:8106/api/market/ticker/list}")
                            String tickerUrl) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.restTemplate = orderRestTemplate;
        this.tickerUrl = tickerUrl;
    }

    /** 条件单行情触发轮询（每 2 秒）。 */
    @Scheduled(fixedDelay = 2000)
    public void scanAndTrigger() {
        List<String> symbols = orderMapper.selectPendingTriggerSymbols();
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        // 拉取全市场最新价：symbol -> lastPrice
        Map<String, Long> latest = fetchLatestPrices();
        if (latest.isEmpty()) {
            log.warn("[trigger] market 行情拉取为空或失败,本轮跳过触发");
            return;
        }
        int triggered = 0;
        for (String symbol : symbols) {
            Long lastPrice = latest.get(symbol);
            if (lastPrice == null) {
                continue;
            }
            for (Order o : orderMapper.selectPendingConditionalOrders(symbol)) {
                if (shouldTrigger(o, lastPrice)) {
                    try {
                        orderService.activateConditional(o);
                        triggered++;
                        log.info("[trigger] 条件单{}触发激活 symbol={} type={} last={} trigger={}",
                                o.getOrderNo(), symbol, o.getTriggerType(), lastPrice, o.getTriggerPrice());
                    } catch (Exception e) {
                        log.error("[trigger] 条件单{}激活异常 symbol={}: {}",
                                o.getOrderNo(), symbol, e.getMessage(), e);
                    }
                }
            }
        }
        if (triggered > 0) {
            log.info("[trigger] 本轮共激活条件单 {} 笔", triggered);
        }
    }

    /** 判断是否满足触发条件：止盈(1)=lastPrice>=triggerPrice；止损(2)=lastPrice<=triggerPrice。 */
    private boolean shouldTrigger(Order o, long lastPrice) {
        return matchesTrigger(o.getTriggerType(), o.getTriggerPrice(), lastPrice);
    }

    /**
     * 触发判定纯逻辑（可独立单测）：止盈=lastPrice>=triggerPrice；止损=lastPrice<=triggerPrice。
     *
     * @param triggerType  条件单类型（1=止盈 2=止损）
     * @param triggerPrice 触发价
     * @param lastPrice    最新价
     */
    static boolean matchesTrigger(Integer triggerType, Long triggerPrice, long lastPrice) {
        if (triggerType == null || triggerPrice == null) {
            return false;
        }
        if (triggerType == OrderConstant.TRIGGER_TYPE_TAKE_PROFIT) {
            return lastPrice >= triggerPrice;
        }
        if (triggerType == OrderConstant.TRIGGER_TYPE_STOP_LOSS) {
            return lastPrice <= triggerPrice;
        }
        return false;
    }

    /** 从 market 服务拉取全市场最新价（Result&lt;List&lt;TickerVO&gt;&gt; → symbol->lastPrice）。 */
    private Map<String, Long> fetchLatestPrices() {
        try {
            ParameterizedTypeReference<Result<List<MarketTicker>>> typeRef =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<Result<List<MarketTicker>>> resp =
                    restTemplate.exchange(tickerUrl, HttpMethod.GET, null, typeRef);
            Result<List<MarketTicker>> body = resp.getBody();
            if (body == null || !body.isSuccess() || body.getData() == null) {
                log.warn("[trigger] market ticker 响应异常 code={} msg={}", body == null ? "null" : body.getCode(),
                        body == null ? "null" : body.getMessage());
                return Collections.emptyMap();
            }
            return body.getData().stream()
                    .filter(t -> t.getSymbol() != null && t.getLastPrice() != null)
                    .collect(Collectors.toMap(MarketTicker::getSymbol, MarketTicker::getLastPrice,
                            (a, b) -> b));
        } catch (Exception e) {
            log.warn("[trigger] 拉取 market ticker 异常: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
