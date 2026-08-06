package com.web3.exchange.chain.service;

import com.web3.exchange.chain.entity.Chain;

import java.util.List;

/**
 * 链配置服务。
 */
public interface ChainService {

    /** 启用扫描且正常的链（scan_enabled=1 && status=1），用于注册 Web3j 与调度扫描。 */
    List<Chain> listEnabled();

    /** 按 chainCode 查链。 */
    Chain getByChainCode(String chainCode);
}
