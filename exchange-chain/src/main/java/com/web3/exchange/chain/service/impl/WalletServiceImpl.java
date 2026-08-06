package com.web3.exchange.chain.service.impl;

import com.web3.exchange.chain.config.ChainProperties;
import com.web3.exchange.chain.service.WalletService;
import com.web3.exchange.common.exception.web3.WalletException;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.util.Arrays;

/**
 * 热钱包服务实现：私钥加载（非明文，仅 Mock/测试网）+ 签名自检。
 * <p>自检采用「对同一 rawTx+chainId 重新签名比较 r/s」：ECDSA 签名确定性（RFC 6979），
 * 重签一致即证明签名确由热钱包私钥产生，规避了 EIP-155 大 v 值签名的恢复复杂度。</p>
 */
@Service
public class WalletServiceImpl implements WalletService {

    private final ChainProperties chainProperties;
    private final Credentials credentials;

    public WalletServiceImpl(ChainProperties chainProperties) {
        this.chainProperties = chainProperties;
        String pk = chainProperties.getHotWallet() == null ? null : chainProperties.getHotWallet().getPrivateKey();
        if (pk == null || pk.isBlank()) {
            throw new WalletException("热钱包私钥未配置（chain.hot-wallet.private-key），仅 Mock/测试网可用");
        }
        try {
            this.credentials = Credentials.create(pk.startsWith("0x") ? pk.substring(2) : pk);
        } catch (Exception e) {
            throw new WalletException("热钱包私钥非法: " + e.getMessage());
        }
    }

    @Override
    public Credentials getCredentials() {
        return credentials;
    }

    @Override
    public boolean verifySigner(byte[] signedRaw, RawTransaction rawTx, long chainId) {
        try {
            Sign.SignatureData signedSig = ((SignedRawTransaction) TransactionDecoder.decode(Numeric.toHexString(signedRaw))).getSignatureData();
            byte[] reSigned = TransactionEncoder.signMessage(rawTx, chainId, credentials);
            Sign.SignatureData reSignedSig = ((SignedRawTransaction) TransactionDecoder.decode(Numeric.toHexString(reSigned))).getSignatureData();
            return Arrays.equals(signedSig.getR(), reSignedSig.getR())
                    && Arrays.equals(signedSig.getS(), reSignedSig.getS());
        } catch (Exception e) {
            throw new WalletException("签名自检失败: " + e.getMessage());
        }
    }
}
