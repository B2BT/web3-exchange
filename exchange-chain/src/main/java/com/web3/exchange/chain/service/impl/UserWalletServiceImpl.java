package com.web3.exchange.chain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.chain.config.ChainProperties;
import com.web3.exchange.chain.crypto.AesGcmCrypto;
import com.web3.exchange.chain.crypto.Bip44Utils;
import com.web3.exchange.chain.dto.WalletBalanceVO;
import com.web3.exchange.chain.dto.WalletCreateRequest;
import com.web3.exchange.chain.dto.WalletImportRequest;
import com.web3.exchange.chain.dto.WalletSendRequest;
import com.web3.exchange.chain.dto.WalletSendResultVO;
import com.web3.exchange.chain.dto.WalletVO;
import com.web3.exchange.chain.entity.Chain;
import com.web3.exchange.chain.entity.Coin;
import com.web3.exchange.chain.entity.UserWallet;
import com.web3.exchange.chain.mapper.UserWalletMapper;
import com.web3.exchange.chain.registry.ChainRegistry;
import com.web3.exchange.chain.service.ChainService;
import com.web3.exchange.chain.service.CoinService;
import com.web3.exchange.chain.service.UserWalletService;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户自托管钱包服务实现。
 * <p>创建/导入 → 派生地址 → 私钥/助记词 AES-GCM 加密入库（明文不落库）；余额查询走 chainRegistry
 * 的 Web3j（原生币 eth_getBalance / ERC-20 eth_call balanceOf）；转账解密钱包私钥离线签名后广播。
 * 金额 Long 最小单位。</p>
 */
@Slf4j
@Service
public class UserWalletServiceImpl extends ServiceImpl<UserWalletMapper, UserWallet>
        implements UserWalletService {

    private static final String COIN_NATIVE = "COIN";
    private static final String COIN_TOKEN = "TOKEN";
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final ChainProperties chainProperties;
    private final CoinService coinService;
    private final ChainRegistry chainRegistry;
    private final ChainService chainService;

    public UserWalletServiceImpl(ChainProperties chainProperties, CoinService coinService,
                                 ChainRegistry chainRegistry, ChainService chainService) {
        this.chainProperties = chainProperties;
        this.coinService = coinService;
        this.chainRegistry = chainRegistry;
        this.chainService = chainService;
    }

    @Override
    public WalletVO create(WalletCreateRequest req) {
        String mnemonic = Bip44Utils.generateMnemonic();
        String address = Bip44Utils.deriveAddressFromMnemonic(mnemonic, req.getChainCode());
        String privateKey = Bip44Utils.derivePrivateKeyFromMnemonic(mnemonic, req.getChainCode());
        String secret = encryptSecret();

        UserWallet w = new UserWallet()
                .setUserId(req.getUserId())
                .setChainCode(req.getChainCode())
                .setWalletType("HD")
                .setMnemonicEnc(AesGcmCrypto.encrypt(mnemonic, secret))
                .setPrivateKeyEnc(AesGcmCrypto.encrypt(privateKey, secret))
                .setAddress(address)
                .setAddressType("SELF")
                .setName(req.getName())
                .setStatus(1);
        w.setId(IdWorker.getId());
        try {
            this.save(w);
            log.info("[self-wallet] 创建 HD 钱包 user={} chain={} addr={}", req.getUserId(), req.getChainCode(), address);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("同链同名地址钱包已存在");
        }
        WalletVO vo = toVO(w);
        vo.setMnemonic(mnemonic); // 明文助记词仅本次返回一次
        return vo;
    }

    @Override
    public WalletVO importWallet(WalletImportRequest req) {
        String secret = encryptSecret();
        UserWallet w = new UserWallet()
                .setUserId(req.getUserId())
                .setChainCode(req.getChainCode())
                .setAddressType("SELF")
                .setName(req.getName())
                .setStatus(1);

        boolean hasMnemonic = req.getMnemonic() != null && !req.getMnemonic().isBlank();
        boolean hasKey = req.getPrivateKey() != null && !req.getPrivateKey().isBlank();
        if (hasMnemonic && !hasKey) {
            if (!Bip44Utils.validateMnemonic(req.getMnemonic())) {
                throw new BusinessException("助记词非法（BIP39 校验失败）");
            }
            w.setWalletType("HD");
            w.setAddress(Bip44Utils.deriveAddressFromMnemonic(req.getMnemonic(), req.getChainCode()));
            w.setMnemonicEnc(AesGcmCrypto.encrypt(req.getMnemonic().trim(), secret));
            w.setPrivateKeyEnc(AesGcmCrypto.encrypt(
                    Bip44Utils.derivePrivateKeyFromMnemonic(req.getMnemonic(), req.getChainCode()), secret));
        } else if (hasKey && !hasMnemonic) {
            w.setWalletType("PRIVATE");
            w.setAddress(Bip44Utils.deriveAddressFromPrivateKey(req.getPrivateKey()));
            w.setPrivateKeyEnc(AesGcmCrypto.encrypt(
                    req.getPrivateKey().startsWith("0x") ? req.getPrivateKey().substring(2) : req.getPrivateKey(), secret));
        } else {
            throw new BusinessException("请提供助记词或私钥（二选一）");
        }
        w.setId(IdWorker.getId());
        try {
            this.save(w);
            log.info("[self-wallet] 导入钱包 user={} chain={} type={} addr={}",
                    req.getUserId(), req.getChainCode(), w.getWalletType(), w.getAddress());
        } catch (DuplicateKeyException e) {
            throw new BusinessException("同链同名地址钱包已存在");
        }
        return toVO(w);
    }

    @Override
    public List<WalletVO> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<UserWallet>()
                        .eq(UserWallet::getUserId, userId)
                        .orderByDesc(UserWallet::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public UserWallet getById(Long userId, Long walletId) {
        UserWallet w = getOne(new LambdaQueryWrapper<UserWallet>()
                .eq(UserWallet::getId, walletId)
                .eq(UserWallet::getUserId, userId)
                .last("limit 1"), false);
        if (w == null) {
            throw new NotFoundException("钱包不存在: " + walletId);
        }
        return w;
    }

    @Override
    public List<WalletBalanceVO> balance(Long userId, Long walletId) {
        UserWallet w = getById(userId, walletId);
        Web3j web3j = chainRegistry.get(w.getChainCode());
        List<WalletBalanceVO> result = new ArrayList<>();
        if (web3j == null) {
            return result;
        }
        String address = w.getAddress();
        // 原生币
        for (Coin coin : coinService.listByChain(w.getChainCode())) {
            if (COIN_NATIVE.equalsIgnoreCase(coin.getCoinType())) {
                try {
                    BigInteger bal = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                            .send().getBalance();
                    result.add(toBalanceVO(w, coin, bal));
                } catch (Exception e) {
                    log.warn("[self-wallet] 原生余额查询失败 {}: {}", coin.getSymbol(), e.getMessage());
                }
            } else if (COIN_TOKEN.equalsIgnoreCase(coin.getCoinType())
                    && coin.getContractAddress() != null && !coin.getContractAddress().isBlank()) {
                try {
                    Function f = new Function("balanceOf",
                            List.of(new Address(address)),
                            List.of(new TypeReference<Uint256>() {
                            }));
                    Transaction tx = Transaction.createEthCallTransaction(address, coin.getContractAddress(),
                            FunctionEncoder.encode(f));
                    EthCall call = web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send();
                    List<Type> decoded = FunctionReturnDecoder.decode(call.getValue(), f.getOutputParameters());
                    BigInteger bal = decoded.isEmpty() ? BigInteger.ZERO : (BigInteger) decoded.get(0).getValue();
                    result.add(toBalanceVO(w, coin, bal));
                } catch (Exception e) {
                    log.warn("[self-wallet] 代币余额查询失败 {}: {}", coin.getSymbol(), e.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public WalletSendResultVO send(Long userId, Long walletId, WalletSendRequest req) {
        UserWallet w = getById(userId, walletId);
        if (!ADDRESS_PATTERN.matcher(req.getToAddress()).matches()) {
            throw new BusinessException("转账目标地址非法（需 0x + 40 位 hex）");
        }
        if (req.getAmount() == null || req.getAmount() <= 0) {
            throw new BusinessException("转账金额必须大于 0");
        }
        Coin coin = coinService.getBySymbol(req.getSymbol());
        if (coin == null) {
            throw new NotFoundException("币种不存在: " + req.getSymbol());
        }
        if (!coin.getChainCode().equalsIgnoreCase(w.getChainCode())) {
            throw new BusinessException("币种与钱包所在链不匹配");
        }
        Chain chain = chainService.getByChainCode(w.getChainCode());
        if (chain == null) {
            throw new NotFoundException("链不存在: " + w.getChainCode());
        }
        Web3j web3j = chainRegistry.get(w.getChainCode());
        if (web3j == null) {
            throw new BusinessException("链未注册: " + w.getChainCode());
        }

        // 解密钱包私钥（AES-GCM），明文仅内存使用
        String privateKey = decryptPrivateKey(w);
        Credentials credentials = Credentials.create(privateKey);
        String fromAddr = credentials.getAddress();

        // 组装交易（与提现广播一致）
        BigInteger nonce;
        try {
            nonce = web3j.ethGetTransactionCount(fromAddr, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
        } catch (IOException e) {
            throw new BusinessException("获取 nonce 失败: " + e.getMessage());
        }
        BigInteger gasPrice = clampGasPrice(web3j, chain);
        boolean token = COIN_TOKEN.equalsIgnoreCase(coin.getCoinType());
        BigInteger gasLimit = token ? BigInteger.valueOf(100_000) : BigInteger.valueOf(21_000);
        BigInteger chainId = chain.getChainId() == null ? BigInteger.ONE : BigInteger.valueOf(chain.getChainId());

        RawTransaction rawTx;
        if (token) {
            Function function = new Function("transfer",
                    Arrays.asList(new Address(req.getToAddress()), new Uint256(BigInteger.valueOf(req.getAmount()))),
                    List.of());
            String data = FunctionEncoder.encode(function);
            rawTx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit,
                    coin.getContractAddress(), BigInteger.ZERO, data);
        } else {
            rawTx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit,
                    req.getToAddress(), BigInteger.valueOf(req.getAmount()));
        }

        byte[] signed = TransactionEncoder.signMessage(rawTx, chainId.longValue(), credentials);
        String rawHex = Numeric.toHexString(signed);
        EthSendTransaction resp;
        try {
            resp = web3j.ethSendRawTransaction(rawHex).send();
        } catch (IOException e) {
            throw new BusinessException("广播异常: " + e.getMessage());
        }
        if (resp.hasError()) {
            throw new BusinessException("广播失败: " + resp.getError().getMessage());
        }
        String txHash = resp.getTransactionHash();
        if (txHash == null) {
            throw new BusinessException("广播返回空 hash");
        }
        log.info("[self-wallet] 转账广播 user={} wallet={} symbol={} from={} to={} amount={} tx={}",
                userId, walletId, coin.getSymbol(), fromAddr, req.getToAddress(), req.getAmount(), txHash);

        WalletSendResultVO vo = new WalletSendResultVO();
        vo.setWalletId(walletId);
        vo.setChainCode(w.getChainCode());
        vo.setSymbol(coin.getSymbol());
        vo.setFromAddress(fromAddr);
        vo.setToAddress(req.getToAddress());
        vo.setAmount(req.getAmount());
        vo.setTxHash(txHash);
        return vo;
    }

    /** 解密钱包私钥（AES-GCM），返回无 0x 的 hex。 */
    private String decryptPrivateKey(UserWallet w) {
        if (w.getPrivateKeyEnc() == null || w.getPrivateKeyEnc().isBlank()) {
            throw new BusinessException("该钱包无可用的私钥");
        }
        return AesGcmCrypto.decrypt(w.getPrivateKeyEnc(), encryptSecret());
    }

    private BigInteger clampGasPrice(Web3j web3j, Chain chain) {
        try {
            BigInteger gp = web3j.ethGasPrice().send().getGasPrice();
            if (chain.getMinGasPrice() != null && gp.compareTo(BigInteger.valueOf(chain.getMinGasPrice())) < 0) {
                return BigInteger.valueOf(chain.getMinGasPrice());
            }
            if (chain.getMaxGasPrice() != null && gp.compareTo(BigInteger.valueOf(chain.getMaxGasPrice())) > 0) {
                return BigInteger.valueOf(chain.getMaxGasPrice());
            }
            return gp;
        } catch (Exception e) {
            return BigInteger.valueOf(1_000_000_000L);
        }
    }

    private WalletBalanceVO toBalanceVO(UserWallet w, Coin coin, BigInteger balance) {
        WalletBalanceVO vo = new WalletBalanceVO();
        vo.setChainCode(w.getChainCode());
        vo.setSymbol(coin.getSymbol());
        vo.setCoinType(coin.getCoinType());
        vo.setBalance(balance == null ? "0" : balance.toString());
        vo.setDecimals(coin.getDecimals());
        return vo;
    }

    private String encryptSecret() {
        ChainProperties.SelfWallet sw = chainProperties.getSelfWallet();
        String secret = sw == null ? null : sw.getEncryptSecret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessException("自托管钱包加密密钥未配置（chain.self-wallet.encrypt-secret）");
        }
        return secret;
    }

    private WalletVO toVO(UserWallet w) {
        WalletVO vo = new WalletVO();
        vo.setId(w.getId());
        vo.setUserId(w.getUserId());
        vo.setChainCode(w.getChainCode());
        vo.setWalletType(w.getWalletType());
        vo.setAddress(w.getAddress());
        vo.setAddressType(w.getAddressType());
        vo.setName(w.getName());
        vo.setStatus(w.getStatus());
        vo.setCreateTime(w.getCreateTime());
        return vo;
    }
}
