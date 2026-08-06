package com.web3.exchange.order.service;

import com.web3.exchange.common.asset.dto.TransferRequest;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.order.constant.OrderConstant;
import com.web3.exchange.order.entity.Trade;
import com.web3.exchange.order.feign.AssetClient;
import com.web3.exchange.order.mapper.TradeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 成交结算服务：对每笔成交发起 2 笔 asset 过户（计价币 Q + 基础币 B）。
 * <p>
 * 币种流向固定：买单方付计价币收基础币、卖单方付基础币收计价币。requestId 幂等 =
 * tradeNo:Q / tradeNo:B（见 docs/order-domain.md §6.4）。过户失败不改变成交事实，
 * 仅置 settle_status=2，由定时补偿任务幂等重试。
 * </p>
 */
@Slf4j
@Service
public class TradeService {

    private final TradeMapper tradeMapper;
    private final AssetClient assetClient;

    public TradeService(TradeMapper tradeMapper, AssetClient assetClient) {
        this.tradeMapper = tradeMapper;
        this.assetClient = assetClient;
    }

    /**
     * 结算一笔成交：计价币 + 基础币各一笔过户。成功置 settle_status=1，失败置 2（待补偿）。
     */
    public void settle(Trade t) {
        // 计价币过户 Q：买方的 USDT（冻结）→ 卖方可用
        TransferRequest quoteTransfer = new TransferRequest();
        quoteTransfer.setRequestId(t.getSettleQuoteRequestId());
        quoteTransfer.setFromUserId(t.getBuyUserId());
        quoteTransfer.setToUserId(t.getSellUserId());
        quoteTransfer.setSymbol(quoteSymbol(t));
        quoteTransfer.setAmount(t.getQuoteAmount());
        quoteTransfer.setBizType(OrderConstant.BIZ_TRANSFER);
        quoteTransfer.setRefNo(t.getTradeNo());
        Result<?> quoteRes = safeTransfer(quoteTransfer);

        // 基础币过户 B：卖方的 BTC（冻结）→ 买方可用
        TransferRequest baseTransfer = new TransferRequest();
        baseTransfer.setRequestId(t.getSettleBaseRequestId());
        baseTransfer.setFromUserId(t.getSellUserId());
        baseTransfer.setToUserId(t.getBuyUserId());
        baseTransfer.setSymbol(baseSymbol(t));
        baseTransfer.setAmount(t.getQuantity());
        baseTransfer.setBizType(OrderConstant.BIZ_TRANSFER);
        baseTransfer.setRefNo(t.getTradeNo());
        Result<?> baseRes = safeTransfer(baseTransfer);

        boolean ok = isOk(quoteRes) && isOk(baseRes);
        t.setSettleStatus(ok ? 1 : 2);
        tradeMapper.updateById(t);
        if (!ok) {
            log.warn("[order] 成交{}过户失败,置待补偿 settle_status=2. quoteRes={} baseRes={}",
                    t.getTradeNo(), quoteRes, baseRes);
        } else {
            log.info("[order] 成交{}结算完成: Q(USDT {} {}→{}) + B(BTC {} {}→{})",
                    t.getTradeNo(), t.getQuoteAmount(), t.getBuyUserId(), t.getSellUserId(),
                    t.getQuantity(), t.getSellUserId(), t.getBuyUserId());
        }
    }

    /**
     * 定时补偿：对 settle_status=2 的成交幂等重试（requestId 幂等，重复过户不重复扣账）。
     */
    public void compensatePending(String symbol) {
        List<Trade> pending = tradeMapper.selectPendingSettle(symbol);
        for (Trade t : pending) {
            try {
                settle(t);
            } catch (Exception e) {
                log.error("[order] 补偿成交{}失败: {}", t.getTradeNo(), e.getMessage(), e);
            }
        }
    }

    // ---------- 工具 ----------

    private Result<?> safeTransfer(TransferRequest req) {
        try {
            return assetClient.transfer(req);
        } catch (Exception e) {
            log.error("[order] 过户异常 reqId={}: {}", req.getRequestId(), e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    private boolean isOk(Result<?> r) {
        return r != null && r.isSuccess();
    }

    private String quoteSymbol(Trade t) {
        return t.getSymbol().split("/")[1];
    }

    private String baseSymbol(Trade t) {
        return t.getSymbol().split("/")[0];
    }
}
