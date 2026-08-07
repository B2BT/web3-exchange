package com.web3.exchange.chain.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.chain.entity.AssetAddress;
import com.web3.exchange.chain.entity.Chain;
import com.web3.exchange.chain.entity.Coin;
import com.web3.exchange.chain.entity.Deposit;
import com.web3.exchange.chain.dto.DepositVO;

import java.util.List;

/**
 * 充值服务：命中处理、确认数递增与入账。
 */
public interface DepositService {

    /**
     * 处理一条链上入账命中（ERC-20 Transfer 或原生币交易）：
     * 命中用户充币地址 → 幂等落单（uk_tx_hash 防重）。
     */
    void handleTransfer(Chain chain, Coin coin, String fromAddress, String toAddress,
                        long amount, String txHash, long blockHeight);

    /**
     * 确认数递增 + 达标入账（调 asset credit），返回本次入账的充值记录列表（仅新入账的）。
     */
    List<Deposit> confirmAndCredit(Chain chain, long latestBlock);

    /**
     * 按 (chainCode, address, address_type=1, is_active=1) 精确查归属用户；未绑定返回 null。
     */
    AssetAddress findActiveAddress(String chainCode, String address);

    /** 用户充值地址（未绑定返回 null）。 */
    AssetAddress getDepositAddress(Long userId, String chainCode, String symbol);

    /**
     * 获取或自动生成用户充币地址（M1 BIP44 HD 派生）。
     * <p>已绑定则返回既有；否则从主助记词派生一个新地址并持久化到 t_asset_address
     * （address_type=1, is_active=1），幂等。每链一个地址（同链所有币种共用）。</p>
     *
     * @return 用户该链的充币地址（非 null）
     */
    AssetAddress getOrCreateDepositAddress(Long userId, String chainCode, String symbol);

    /** 用户充值记录分页。 */
    Page<DepositVO> pageByUser(Long userId, int page, int size);

    /** 链上交易哈希对应的充值记录（幂等查询）。 */
    Deposit getByTxHash(String txHash);
}
