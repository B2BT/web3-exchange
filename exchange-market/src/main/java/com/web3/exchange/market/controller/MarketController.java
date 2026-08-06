package com.web3.exchange.market.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.market.dto.KlineVO;
import com.web3.exchange.market.dto.TickerVO;
import com.web3.exchange.market.market.MarketAggregator;
import com.web3.exchange.market.market.model.Kline;
import com.web3.exchange.market.market.model.Ticker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 行情对外 REST 接口（/api/market/**，经网关路由，无 context-path 直接转发）。
 * <p>只读行情查询，不产生任何写操作。价格/数量/金额一律 Long 最小单位。</p>
 */
@RestController
@RequestMapping("/api/market")
@Tag(name = "行情对外接口")
public class MarketController {

    private final MarketAggregator aggregator;

    public MarketController(MarketAggregator aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * K线列表：按窗口时间升序返回最近 N 根。
     * @param interval 周期 1m/5m/15m/1h/4h/1d（兼容别名 period）
     * @param limit 默认 200，最大 1000
     */
    @Operation(summary = "K线列表")
    @GetMapping("/kline/list")
    public Result<List<KlineVO>> klineList(@RequestParam("symbol") String symbol,
                                           @RequestParam(value = "interval", required = false) String interval,
                                           @RequestParam(value = "period", required = false) String period,
                                           @RequestParam(value = "limit", defaultValue = "200") Integer limit) {
        String iv = interval != null && !interval.isBlank() ? interval : period;
        if (iv == null || iv.isBlank()) {
            return Result.badRequest("缺少周期参数 interval/period (1m/5m/15m/1h/4h/1d)");
        }
        int cap = limit == null ? 200 : Math.min(Math.max(limit, 1), 1000);
        List<Kline> klines = aggregator.getKlines(symbol, iv, cap);
        List<KlineVO> vos = new ArrayList<>(klines.size());
        for (Kline k : klines) {
            KlineVO vo = new KlineVO();
            vo.setSymbol(k.getSymbol());
            vo.setInterval(k.getInterval());
            vo.setOpenTime(k.getOpenTime());
            vo.setOpen(k.getOpen());
            vo.setHigh(k.getHigh());
            vo.setLow(k.getLow());
            vo.setClose(k.getClose());
            vo.setVolume(k.getVolume());
            vo.setQuoteVolume(k.getQuoteVolume());
            vos.add(vo);
        }
        return Result.success(vos);
    }

    /**
     * 全市场 ticker 列表。
     */
    @Operation(summary = "全市场ticker列表")
    @GetMapping("/ticker/list")
    public Result<List<TickerVO>> tickerList() {
        List<Ticker> tickers = aggregator.getTickers();
        List<TickerVO> vos = new ArrayList<>(tickers.size());
        for (Ticker t : tickers) {
            vos.add(toVO(t));
        }
        return Result.success(vos);
    }

    /**
     * 单交易对 ticker；不存在返回 notFound。
     * <p>{symbol:.+} 允许交易对含斜杠（如 BTC/USDT）被完整捕获。</p>
     */
    @Operation(summary = "单交易对ticker")
    @GetMapping("/ticker/{symbol:.+}")
    public Result<TickerVO> ticker(@PathVariable("symbol") String symbol) {
        Ticker t = aggregator.getTicker(symbol);
        if (t == null) {
            return Result.notFound("暂无行情: " + symbol);
        }
        return Result.success(toVO(t));
    }

    private TickerVO toVO(Ticker t) {
        TickerVO vo = new TickerVO();
        vo.setSymbol(t.getSymbol());
        vo.setLastPrice(t.getLastPrice());
        vo.setChange24h(t.getChange24h());
        vo.setHigh24h(t.getHigh24h());
        vo.setLow24h(t.getLow24h());
        vo.setVolume24h(t.getVolume24h());
        vo.setQuoteVolume24h(t.getQuoteVolume24h());
        return vo;
    }
}
