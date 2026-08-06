package com.web3.exchange.asset.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.asset.mq.Topics;
import com.web3.exchange.asset.service.LedgerService;
import com.web3.exchange.common.asset.dto.TransferRequest;
import com.web3.exchange.common.order.dto.TradeSettleDTO;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * ORDER-TRADE 幂等消费者——消费 order 的成交结算指令，幂等过户驱动资金最终一致。
 * <p>
 * 定位：本批次（C2）将「成交过户」从 order 同步 Feign 改为<b>事务消息驱动</b>——order 本地
 * 落库 t_trade 后发 ORDER-TRADE 事务消息（COMMIT 后才投递），asset 收到后对每笔成交做
 * 2 笔过户（计价币 Q：买方→卖方；基础币 B：卖方→买方），完成「下单→撮合→成交→事务消息→过户」闭环。
 * </p>
 * <p>
 * <b>幂等（双层）</b>：① 消费层 Redis SETNX（bizKey=tradeNo，TTL 24h），重复投递/重放直接 ACK 跳过；
 * ② 业务层复用 {@code /internal/asset/transfer} 的 requestId 幂等（tradeNo:Q / tradeNo:B，
 * 见 docs/asset-domain.md §4.2/§5.1），即便 SETNX 失效，transfer 也会按 uk_request_id 兜底不重复扣账。
 * </p>
 */
@Component
@RocketMQMessageListener(
        topic = Topics.ORDER_TRADE,
        consumerGroup = "asset-order-trade-group",
        selectorExpression = "*"   // 订阅全部 Tag
)
public class TradeSettleConsumer implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(TradeSettleConsumer.class);
    private static final String DEDUP_KEY_PREFIX = "mq:dedup:ORDER-TRADE:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LedgerService ledgerService;

    public TradeSettleConsumer(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               LedgerService ledgerService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ledgerService = ledgerService;
    }

    @Override
    public void onMessage(MessageExt msg) {
        String bizKey = msg.getKeys() != null ? msg.getKeys() : msg.getMsgId();

        // 1. 消费层幂等去重：SETNX，重复则跳过（已处理过）
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(DEDUP_KEY_PREFIX + bizKey, "1", DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.info("重复消息，幂等跳过。topic={}, bizKey={}", msg.getTopic(), bizKey);
            return;
        }

        // 2. 解析结算指令
        TradeSettleDTO dto;
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            dto = objectMapper.readValue(body, TradeSettleDTO.class);
        } catch (Exception e) {
            log.error("消费 ORDER-TRADE 消息体解析失败。bizKey={}, err={}", bizKey, e.getMessage(), e);
            throw new IllegalArgumentException("ORDER-TRADE 消息体解析失败: " + bizKey, e); // 触发重投
        }
        if (dto == null || dto.getTradeNo() == null || dto.getBuyUserId() == null || dto.getSellUserId() == null) {
            log.error("ORDER-TRADE 消息体不完整，触发重投。bizKey={}, body={}",
                    bizKey, new String(msg.getBody(), StandardCharsets.UTF_8));
            throw new IllegalArgumentException("ORDER-TRADE 消息体不完整: " + bizKey);
        }

        // 3. 幂等过户：计价币 Q（买方→卖方）+ 基础币 B（卖方→买方）
        //    transfer 内部以 requestId（tradeNo:Q / tradeNo:B）幂等兜底，重复不重复扣账
        try {
            TransferRequest quote = buildTransfer(dto, true);
            ledgerService.transfer(quote);
            TransferRequest base = buildTransfer(dto, false);
            ledgerService.transfer(base);
            log.info("消费 ORDER-TRADE 过户成功。tradeNo={}, symbol={}, price={}, qty={}, quoteAmount={}, "
                            + "buy={}, sell={}：Q(计价币 {} {}→{}) + B(基础币 {} {}→{})",
                    dto.getTradeNo(), dto.getSymbol(), dto.getPrice(), dto.getQuantity(), dto.getQuoteAmount(),
                    dto.getBuyUserId(), dto.getSellUserId(),
                    dto.getQuoteCoin(), dto.getBuyUserId(), dto.getSellUserId(),
                    dto.getBaseCoin(), dto.getSellUserId(), dto.getBuyUserId());
        } catch (Exception e) {
            // 过户失败：抛异常触发重投（默认 16 次后进死信）；requestId 幂等保证重投不重复扣账
            log.error("消费 ORDER-TRADE 过户失败，待重投。tradeNo={}, err={}", dto.getTradeNo(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 组装过户请求。{@code quote=true} 计价币过户（买方冻结→卖方可用，requestId=tradeNo:Q）；
     * {@code quote=false} 基础币过户（卖方冻结→买方可用，requestId=tradeNo:B）。
     */
    private TransferRequest buildTransfer(TradeSettleDTO dto, boolean quote) {
        TransferRequest req = new TransferRequest();
        if (quote) {
            req.setRequestId(dto.getTradeNo() + ":Q");
            req.setFromUserId(dto.getBuyUserId());
            req.setToUserId(dto.getSellUserId());
            req.setSymbol(dto.getQuoteCoin());
            req.setAmount(dto.getQuoteAmount());
        } else {
            req.setRequestId(dto.getTradeNo() + ":B");
            req.setFromUserId(dto.getSellUserId());
            req.setToUserId(dto.getBuyUserId());
            req.setSymbol(dto.getBaseCoin());
            req.setAmount(dto.getQuantity());
        }
        req.setBizType("TRANSFER");
        req.setRefNo(dto.getTradeNo());
        req.setRemark("ORDER-TRADE 成交过户");
        return req;
    }
}
