package com.web3.exchange.chain.scanner;

import com.web3.exchange.chain.config.ChainProperties;
import com.web3.exchange.chain.entity.Chain;
import com.web3.exchange.chain.entity.Coin;
import com.web3.exchange.chain.mapper.DepositMapper;
import com.web3.exchange.chain.registry.ChainRegistry;
import com.web3.exchange.chain.service.ChainService;
import com.web3.exchange.chain.service.CoinService;
import com.web3.exchange.chain.service.DepositService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 充值区块扫描器（轮询，非订阅）：按启用链拉最新块 → 批扫 ERC-20 Transfer 日志/原生币块内交易 →
 * 命中用户充币地址幂等落单 → 确认数达标由 {@link DepositService#confirmAndCredit} 调 asset credit 入账。
 * <p>Redis 游标 chain:scan:{chainCode}:cursor 断点续扫；启动缺失时按 max(block_height) 回退 safety-window 补块。</p>
 */
@Slf4j
@Component
public class DepositScanner {

    /** ERC-20 Transfer(address,address,uint256) 事件签名哈希（ERC-721 同签名，第三 topic 为 tokenId） */
    public static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    /** ERC-1155 TransferSingle(address,address,address,uint256,uint256) 事件签名哈希 */
    public static final String TRANSFER_SINGLE_TOPIC =
            "0xc3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f62";

    /** ERC-1155 TransferBatch(address,address,address,uint256[],uint256[]) 事件签名哈希 */
    public static final String TRANSFER_BATCH_TOPIC =
            "0x4a39dc06d4c0dbc64b70af90fd698a233a518aa5d07e595d983b8c0526c8f7fb";

    private final ChainProperties chainProperties;
    private final StringRedisTemplate redis;
    private final ChainRegistry chainRegistry;
    private final ChainService chainService;
    private final CoinService coinService;
    private final DepositService depositService;
    private final DepositMapper depositMapper;

    public DepositScanner(ChainProperties chainProperties, StringRedisTemplate redis,
                          ChainRegistry chainRegistry, ChainService chainService,
                          CoinService coinService, DepositService depositService,
                          DepositMapper depositMapper) {
        this.chainProperties = chainProperties;
        this.redis = redis;
        this.chainRegistry = chainRegistry;
        this.chainService = chainService;
        this.coinService = coinService;
        this.depositService = depositService;
        this.depositMapper = depositMapper;
    }

    @Scheduled(fixedDelayString = "${chain.scan.interval-ms:3000}", initialDelay = 15000)
    public void scanAll() {
        if (!chainProperties.getScan().isEnabled()) {
            return;
        }
        for (Chain c : chainService.listEnabled()) {
            if (!"EVM".equalsIgnoreCase(c.getChainType())) {
                continue; // 本期只激活 EVM 链
            }
            Web3j web3j = chainRegistry.get(c.getChainCode());
            if (web3j == null) {
                continue;
            }
            try {
                scanChain(c, web3j);
            } catch (Exception e) {
                log.warn("[scan] 链 {} 扫描异常: {}", c.getChainCode(), e.getMessage());
            }
        }
    }

    private void scanChain(Chain c, Web3j web3j) throws Exception {
        BigInteger latest = web3j.ethBlockNumber().send().getBlockNumber();
        long latestL = latest.longValue();
        long cursor = loadCursor(c.getChainCode());
        while (cursor <= latestL) {
            long to = Math.min(cursor + chainProperties.getScan().getBatchSize() - 1, latestL);
            scanRange(c, web3j, cursor, to);
            saveCursor(c.getChainCode(), to + 1);
            cursor = to + 1;
        }
        // 本周期扫完：确认数递增 + 达标入账
        depositService.confirmAndCredit(c, latestL);
    }

    private void scanRange(Chain c, Web3j web3j, long from, long to) {
        if (to < from) {
            return;
        }
        List<Coin> coins = coinService.listByChain(c.getChainCode());

        // ===== ERC-20/ERC-721/ERC-1155 合约代币：eth_getLogs(标准 Transfer 事件) =====
                Map<String, Coin> contractCoinMap = new HashMap<>();
                for (Coin coin : coins) {
                    if ("TOKEN".equalsIgnoreCase(coin.getCoinType())
                            && coin.getDepositEnabled() != null && coin.getDepositEnabled() == 1
                            && coin.getContractAddress() != null && !coin.getContractAddress().isBlank()) {
                        contractCoinMap.put(coin.getContractAddress().toLowerCase(Locale.ROOT), coin);
                    }
                }
                if (!contractCoinMap.isEmpty()) {
                    try {
                        EthFilter filter = new EthFilter(
                                DefaultBlockParameter.valueOf(BigInteger.valueOf(from)),
                                DefaultBlockParameter.valueOf(BigInteger.valueOf(to)),
                                new java.util.ArrayList<>(contractCoinMap.keySet()));
                        // 兼容三标准：ERC-20/721 Transfer 同签名；ERC-1155 用 TransferSingle/TransferBatch（topic0 不同，
                        // 不过滤 topic0，靠 topic0 识别后再分流，避免多 filter 重复扫块）
                        filter.addOptionalTopics(
                                TRANSFER_TOPIC,           // ERC-20/721 Transfer(address,address,uint256)
                                TRANSFER_SINGLE_TOPIC,    // ERC-1155 TransferSingle(address,address,address,uint256,uint256)
                                TRANSFER_BATCH_TOPIC);    // ERC-1155 TransferBatch(address,address,address,uint256[],uint256[])
                        EthLog ethLog = web3j.ethGetLogs(filter).send();
                        if (ethLog.hasError()) {
                            log.warn("[scan] {} eth_getLogs 错误: {}", c.getChainCode(), ethLog.getError().getMessage());
                        } else {
                            for (EthLog.LogResult logResult : ethLog.getLogs()) {
                                if (logResult instanceof Log lg) {
                                    handleTransferLog(c, lg, contractCoinMap);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[scan] {} eth_getLogs 异常: {}", c.getChainCode(), e.getMessage());
                    }
                }

        // ===== 原生币（ETH 等）：eth_getBlockByNumber(fullTx=true) 扫 tx.to == 充币地址 =====
        for (Coin coin : coins) {
            if ("COIN".equalsIgnoreCase(coin.getCoinType())
                    && coin.getDepositEnabled() != null && coin.getDepositEnabled() == 1) {
                try {
                    EthBlock.Block block = web3j.ethGetBlockByNumber(
                            DefaultBlockParameter.valueOf(BigInteger.valueOf(to)), true).send().getBlock();
                    if (block == null) {
                        continue;
                    }
                    for (EthBlock.TransactionResult tr : block.getTransactions()) {
                        if (tr instanceof Transaction tx && tx.getTo() != null) {
                            long amount = tx.getValue() == null ? 0 : tx.getValue().longValue();
                            depositService.handleTransfer(c, coin, tx.getFrom(), tx.getTo(),
                                    amount, tx.getHash(), to);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[scan] {} 原生币扫块异常: {}", c.getChainCode(), e.getMessage());
                }
            }
        }
    }

    private void handleTransferLog(Chain c, Log lg, Map<String, Coin> contractCoinMap) {
        try {
            List<String> topics = lg.getTopics();
            if (topics == null || topics.isEmpty()) {
                return;
            }
            String contract = lg.getAddress() == null ? null : lg.getAddress().toLowerCase(Locale.ROOT);
            Coin coin = contract == null ? null : contractCoinMap.get(contract);
            if (coin == null) {
                return;
            }
            String topic0 = topics.get(0);
            long blockHeight = lg.getBlockNumber().longValue();
            if (TRANSFER_SINGLE_TOPIC.equalsIgnoreCase(topic0)) {
                // ERC-1155 TransferSingle(operator,from,to,id,value)：topics[1..3]=operator/from/to，data=id;value
                if (topics.size() < 4) return;
                String from = "0x" + topics.get(2).substring(26);
                String to = "0x" + topics.get(3).substring(26);
                String[] data = lg.getData() == null ? new String[0] : lg.getData().substring(2).split("(?<=\\G.{64})");
                if (data.length < 2) return;
                String tokenId = Numeric.toBigInt("0x" + data[0]).toString();
                long amount = Numeric.toBigInt("0x" + data[1]).longValue();
                depositService.handleNftTransfer(c, coin, from, to, tokenId, amount,
                        lg.getTransactionHash(), blockHeight);
            } else if (TRANSFER_BATCH_TOPIC.equalsIgnoreCase(topic0)) {
                // ERC-1155 TransferBatch(operator,from,to,ids,values)：topics[1..3]=operator/from/to，data=ids;values
                if (topics.size() < 4) return;
                String from = "0x" + topics.get(2).substring(26);
                String to = "0x" + topics.get(3).substring(26);
                String data = lg.getData() == null ? "" : lg.getData().substring(2);
                // 解析两个动态数组 ids[] 与 values[]（ABI 编码：offset1;len1;ids...;offset2;len2;values...）
                java.util.List<Long> ids = new java.util.ArrayList<>();
                java.util.List<Long> values = new java.util.ArrayList<>();
                if (data.length() >= 128) {
                    int offIds = Numeric.toBigInt("0x" + data.substring(0, 64)).intValue() * 2;
                    int lenIds = Numeric.toBigInt("0x" + data.substring(offIds, offIds + 64)).intValue();
                    for (int i = 0; i < lenIds; i++) {
                        ids.add(Numeric.toBigInt("0x" + data.substring(offIds + 64 + i * 64, offIds + 128 + i * 64)).longValue());
                    }
                    int offVals = Numeric.toBigInt("0x" + data.substring(64, 128)).intValue() * 2;
                    int lenVals = Numeric.toBigInt("0x" + data.substring(offVals, offVals + 64)).intValue();
                    for (int i = 0; i < lenVals; i++) {
                        values.add(Numeric.toBigInt("0x" + data.substring(offVals + 64 + i * 64, offVals + 128 + i * 64)).longValue());
                    }
                }
                for (int i = 0; i < ids.size() && i < values.size(); i++) {
                    depositService.handleNftTransfer(c, coin, from, to, ids.get(i).toString(), values.get(i),
                            lg.getTransactionHash(), blockHeight);
                }
            } else {
                // ERC-20 / ERC-721：Transfer(from,to,idOrValue)
                if (topics.size() < 3) {
                    return;
                }
                String from = "0x" + topics.get(1).substring(26);
                String to = "0x" + topics.get(2).substring(26);
                if ("ERC-721".equalsIgnoreCase(coin.getTokenStandard())) {
                    // ERC-721：topics[3] 是 tokenId，amount 恒为 1（data 通常为空，不可作 value）
                    if (topics.size() < 4) {
                        return;
                    }
                    String tokenId = Numeric.toBigInt(topics.get(3)).toString();
                    depositService.handleNftTransfer(c, coin, from, to, tokenId, 1L,
                            lg.getTransactionHash(), blockHeight);
                } else {
                    // ERC-20（或未标注）：data 为金额 value
                    BigInteger value = Numeric.toBigInt(lg.getData());
                    depositService.handleTransfer(c, coin, from, to, value.longValue(),
                            lg.getTransactionHash(), blockHeight);
                }
            }
        } catch (Exception e) {
            log.warn("[scan] Transfer 日志解析失败: {}", e.getMessage());
        }
    }

    private long loadCursor(String chainCode) {
        String key = cursorKey(chainCode);
        String v = redis.opsForValue().get(key);
        if (v != null) {
            return Long.parseLong(v);
        }
        // 重启补块：从该链待处理 max(block_height) 回退 safety-window
        long max = depositMapper.maxBlockHeightForPending(chainCode);
        long start = max > 0
                ? Math.max(max - chainProperties.getScan().getSafetyWindow(), 0)
                : chainProperties.getScan().getStartBlock();
        log.info("[scan] 游标缺失 {}，回退补块起点 {}", chainCode, start);
        return start;
    }

    private void saveCursor(String chainCode, long cursor) {
        redis.opsForValue().set(cursorKey(chainCode), String.valueOf(cursor));
    }

    private String cursorKey(String chainCode) {
        return "chain:scan:" + chainCode + ":cursor";
    }
}
