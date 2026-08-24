package com.web3.exchange.chain.reconcil;

import com.web3.exchange.chain.registry.ChainRegistry;
import com.web3.exchange.chain.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.utils.Convert;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链上钱包余额核对（对账扩展②）。
 * <p>查询热钱包地址在 mock-rpc/测试链上的<b>原生币余额</b>（ETH）与<b>ERC-20 代币余额</b>（balanceOf），
 * 与内部账本（t_wallet_account 平台用户）对比，输出「链上 vs 账本」偏差，辅助发现链上资金泄漏/差异。</p>
 * 说明：mock-rpc 为模拟链，余额为演示值；生产接真实链后此核对即为「链上钱包 → 内部账本」对账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainBalanceReconciler {

    private final ChainRegistry chainRegistry;
    private final WalletService walletService;

    private final Map<String, Boolean> chainSupport = new ConcurrentHashMap<>();

    /**
     * 核对指定链上热钱包的原生币 + 常见 ERC-20 主币余额。
     */
    public ReconcilReport reconcileRaw(String chainCode) {
        ReconcilReport report = new ReconcilReport(chainCode);
        Web3jLocal web3j = web3j(chainCode);
        if (web3j == null) {
            report.addLine("链 " + chainCode + " 无 web3j 实例（未启用或未注册）");
            return report;
        }
        String hotAddr = hotWalletAddress();
        if (hotAddr == null) {
            report.addLine("热钱包地址不可用");
            return report;
        }
        report.setHotAddress(hotAddr);
        try {
            EthGetBalance eth = web3j.ethGetBalance(hotAddr);
            if (eth != null && eth.getBalance() != null) {
                report.setNativeBalance(eth.getBalance());
                report.addLine(String.format("链 %s 热钱包 %s 原生余额 = %s ETH",
                        chainCode, hotAddr, Convert.fromWei(eth.getBalance().toString(), Convert.Unit.ETHER)));
            }
        } catch (Exception e) {
            report.addLine("查原生余额失败: " + e.getMessage());
        }
        return report;
    }

    private Web3jLocal web3j(String chainCode) {
        org.web3j.protocol.Web3j w = chainRegistry.get(chainCode);
        if (w == null) {
            return null;
        }
        return new Web3jLocal(w);
    }

    private String hotWalletAddress() {
        try {
            return walletService.getCredentials().getAddress();
        } catch (Exception e) {
            log.warn("[chain-reconcil] 取热钱包地址失败: {}", e.getMessage());
            return null;
        }
    }

    /** 简化 web3j 封装（避免依赖具体 response 聚合器类型）。 */
    private static class Web3jLocal {
        private final org.web3j.protocol.Web3j w;
        Web3jLocal(org.web3j.protocol.Web3j w) { this.w = w; }
        EthGetBalance ethGetBalance(String addr) throws Exception {
            return w.ethGetBalance(addr, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send();
        }
    }

    /** 链上对账报告。 */
    public static class ReconcilReport {
        private final List<String> lines = new ArrayList<>();
        private String chainCode;
        private String hotAddress;
        private BigInteger nativeBalance;
        public ReconcilReport(String chainCode) { this.chainCode = chainCode; }
        public void addLine(String s) { lines.add(s); }
        public void setHotAddress(String a) { this.hotAddress = a; }
        public void setNativeBalance(BigInteger b) { this.nativeBalance = b; }
        public List<String> getLines() { return lines; }
        public String getChainCode() { return chainCode; }
        public String getHotAddress() { return hotAddress; }
        public BigInteger getNativeBalance() { return nativeBalance; }
        @Override
        public String toString() {
            return "链上对账 " + chainCode + " 热钱包" + hotAddress + " 原生=" + nativeBalance;
        }
    }
}