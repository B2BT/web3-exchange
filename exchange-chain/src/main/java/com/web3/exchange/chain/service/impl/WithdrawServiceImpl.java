package com.web3.exchange.chain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.chain.config.ChainProperties;
import com.web3.exchange.chain.dto.AuditRequest;
import com.web3.exchange.chain.dto.WithdrawApplyRequest;
import com.web3.exchange.chain.dto.WithdrawVO;
import com.web3.exchange.chain.entity.Chain;
import com.web3.exchange.chain.entity.Coin;
import com.web3.exchange.chain.entity.Withdraw;
import com.web3.exchange.chain.feign.AssetClient;
import com.web3.exchange.chain.mapper.WithdrawMapper;
import com.web3.exchange.chain.registry.ChainRegistry;
import com.web3.exchange.chain.service.ChainService;
import com.web3.exchange.chain.service.CoinService;
import com.web3.exchange.chain.service.WalletService;
import com.web3.exchange.chain.service.WithdrawService;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.asset.dto.TransferRequest;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.exception.NotFoundException;
import com.web3.exchange.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 提现服务实现——状态机 0待审核→1审核中→2处理中(已冻结上链)→3成功/4拒绝/5失败回滚。
 * <p>资金铁律：绝不直接改余额，冻结走 freeze、成功扣减走 transfer、失败回滚走 unfreeze；
 * 三者 requestId 分别为 withdraw.requestId / +":W" / +":U"，互不冲突、各自幂等。</p>
 */
@Slf4j
@Service
public class WithdrawServiceImpl extends ServiceImpl<WithdrawMapper, Withdraw> implements WithdrawService {

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final ChainService chainService;
    private final CoinService coinService;
    private final ChainRegistry chainRegistry;
    private final WalletService walletService;
    private final ChainProperties chainProperties;
    private final AssetClient assetClient;

    public WithdrawServiceImpl(ChainService chainService, CoinService coinService,
                               ChainRegistry chainRegistry, WalletService walletService,
                               ChainProperties chainProperties, AssetClient assetClient) {
        this.chainService = chainService;
        this.coinService = coinService;
        this.chainRegistry = chainRegistry;
        this.walletService = walletService;
        this.chainProperties = chainProperties;
        this.assetClient = assetClient;
    }

    @Override
    public WithdrawVO apply(WithdrawApplyRequest req) {
        if (!ADDRESS_PATTERN.matcher(req.getToAddress()).matches()) {
            throw new BusinessException("提现地址非法（需 0x + 40 位 hex）");
        }
        Coin coin = coinService.getBySymbol(req.getSymbol());
        if (coin == null) {
            throw new NotFoundException("币种不存在: " + req.getSymbol());
        }
        Chain chain = chainService.getByChainCode(req.getChainCode());
        if (chain == null) {
            throw new NotFoundException("链不存在: " + req.getChainCode());
        }
        if (coin.getWithdrawEnabled() == null || coin.getWithdrawEnabled() != 1) {
            throw new BusinessException("该币种未开放提现");
        }
        if (!coin.getChainCode().equalsIgnoreCase(chain.getChainCode())) {
            throw new BusinessException("币种与链不匹配");
        }
        if (coin.getMinWithdraw() != null && req.getAmount() < coin.getMinWithdraw()) {
            throw new BusinessException("低于最小提现额");
        }
        if (coin.getMaxWithdraw() != null && req.getAmount() > coin.getMaxWithdraw()) {
            throw new BusinessException("超过单笔最大提现额");
        }
        long fee = coin.getWithdrawFee() == null ? 0 : coin.getWithdrawFee();
        long realAmount = req.getAmount() - fee;
        if (realAmount <= 0) {
            throw new BusinessException("实际到账金额必须大于0");
        }

        long id = IdWorker.getId();
        Withdraw w = new Withdraw()
                .setRequestId("WD:" + id)
                .setUserId(req.getUserId())
                .setCoinId(coin.getId())
                .setSymbol(coin.getSymbol())
                .setChainCode(chain.getChainCode())
                .setToAddress(req.getToAddress())
                .setAmount(req.getAmount())
                .setTokenId(req.getTokenId())
                .setFee(fee)
                .setRealAmount(realAmount)
                .setStatus(0);
        w.setId(id);
        try {
            this.save(w);
            log.info("[withdraw] 申请落单 id={} user={} symbol={} amount={} real={}",
                    id, req.getUserId(), coin.getSymbol(), req.getAmount(), realAmount);
        } catch (DuplicateKeyException e) {
            // uk_request_id 幂等（重复申请撞号时返回已有单）
            Withdraw exist = getById(id);
            if (exist != null) {
                return toVO(exist);
            }
            throw new BusinessException("申请冲突，请重试");
        }
        return toVO(w);
    }

    @Override
    public WithdrawVO audit(Long withdrawId, AuditRequest req) {
        Withdraw w = getById(withdrawId);
        if (w == null) {
            throw new NotFoundException("提现单不存在: " + withdrawId);
        }
        if (w.getStatus() == null || (w.getStatus() != 0 && w.getStatus() != 1 && w.getStatus() != 8)) {
            throw new BusinessException("当前状态不可审核: status=" + w.getStatus());
        }
        boolean approve = Boolean.TRUE.equals(req.getApprove());
        String remark = req.getRemark();
        String reviewer = req.getReviewer() == null || req.getReviewer().isBlank() ? "admin" : req.getReviewer();
        int level = req.getLevel() == null ? 1 : req.getLevel();

        if (level == 2) {
            // ===== 复核（第二审核员）=====
            if (w.getStatus() != null && w.getStatus() != 8) {
                throw new BusinessException("复核仅对'待复核'状态(status=8)的提现单执行");
            }
            // 复核人须与初审不同（同一审核员不可既审又复）
            if (reviewer.equalsIgnoreCase(w.getAuditBy()) && w.getAuditBy() != null) {
                throw new BusinessException("复核人不能与初审为同一审核员");
            }
            if (approve) {
                // 复核通过 → 冻结 + 上链（原初审的步骤 2/3 逻辑）
                return reviewApproveAndChain(withdrawId, w, reviewer, remark);
            } else {
                return doReject(withdrawId, reviewer, remark);
            }
        }

        // ===== 初审（第一审核员）=====
        LambdaUpdateWrapper<Withdraw> uw = new LambdaUpdateWrapper<>();
        uw.eq(Withdraw::getId, withdrawId)
                .in(Withdraw::getStatus, 0, 1)
                .set(Withdraw::getAuditBy, reviewer)
                .set(Withdraw::getAuditTime, LocalDateTime.now())
                .set(Withdraw::getAuditRemark, remark);
        if (approve) {
            // 初审通过 → 进入"待复核"(status=8)，由复核员确认后真正冻结上链
            uw.set(Withdraw::getStatus, 8);
        } else {
            uw.set(Withdraw::getStatus, 4);
        }
        boolean claimed = this.update(uw);
        if (!claimed) {
            throw new BusinessException("审核并发冲突，请重试");
        }
        if (!approve) {
            log.info("[withdraw] 初审拒绝 id={} reviewer={}", withdrawId, reviewer);
            return toVO(getById(withdrawId));
        }
        log.info("[withdraw] 初审通过 待复核 id={} reviewer={}", withdrawId, reviewer);
        return toVO(getById(withdrawId));
    }

    /** 复核通过后执行冻结 + 上链（原初审 approve 的后续逻辑）。 */
    private WithdrawVO reviewApproveAndChain(Long withdrawId, Withdraw w, String reviewer, String remark) {
        // 状态抢占：8 → 处理中(2)
        LambdaUpdateWrapper<Withdraw> uw = new LambdaUpdateWrapper<>();
        uw.eq(Withdraw::getId, withdrawId)
                .eq(Withdraw::getStatus, 8)
                .set(Withdraw::getAuditBy, reviewer)
                .set(Withdraw::getAuditTime, LocalDateTime.now())
                .set(Withdraw::getAuditRemark, remark)
                .set(Withdraw::getStatus, 2);
        boolean claimed = this.update(uw);
        if (!claimed) {
            throw new BusinessException("复核并发冲突，请重试");
        }

        // 冻结（进入处理中前锁定资金）
        Chain chain = chainService.getByChainCode(w.getChainCode());
        Coin coin = coinService.getBySymbol(w.getSymbol());
        FreezeRequest freezeReq = new FreezeRequest();
        freezeReq.setRequestId(w.getRequestId());
        freezeReq.setUserId(w.getUserId());
        freezeReq.setSymbol(w.getSymbol());
        freezeReq.setAmount(w.getAmount());
        freezeReq.setBizType("FREEZE");
        freezeReq.setRefNo(String.valueOf(withdrawId));
        freezeReq.setRemark("提现冻结(复核通过)");
        try {
            Result<LedgerVO> fr = assetClient.freeze(freezeReq);
            if (fr == null || !fr.isSuccess() || fr.getData() == null) {
                failRollback(withdrawId, "冻结失败: " + (fr == null ? "null" : fr.getMessage()));
                return toVO(getById(withdrawId));
            }
            this.update(new LambdaUpdateWrapper<Withdraw>()
                    .eq(Withdraw::getId, withdrawId)
                    .set(Withdraw::getFreezeLedgerId, fr.getData().getId()));
            log.info("[withdraw] 复核通过 冻结成功 id={} ledgerId={}", withdrawId, fr.getData().getId());
        } catch (Exception e) {
            failRollback(withdrawId, "冻结异常: " + e.getMessage());
            return toVO(getById(withdrawId));
        }

        // 离线签名 + 广播（或进入冷钱包待签）
        try {
            String txHash = broadcast(w, chain, coin);
            if (txHash != null && txHash.startsWith("COLD_PENDING_")) {
                log.info("[withdraw] 已进入冷钱包待签 id={}", withdrawId);
            } else {
                this.update(new LambdaUpdateWrapper<Withdraw>()
                        .eq(Withdraw::getId, withdrawId)
                        .set(Withdraw::getTxHash, txHash));
                log.info("[withdraw] 复核通过 广播成功 id={} txHash={}", withdrawId, txHash);
            }
        } catch (Exception e) {
            log.warn("[withdraw] 广播失败 id={}: {}", withdrawId, e.getMessage());
            unfreezeRollback(withdrawId, "上链失败: " + e.getMessage());
        }
        return toVO(getById(withdrawId));
    }

    private WithdrawVO doReject(Long withdrawId, String reviewer, String remark) {
        this.update(new LambdaUpdateWrapper<Withdraw>()
                .eq(Withdraw::getId, withdrawId)
                .set(Withdraw::getAuditBy, reviewer)
                .set(Withdraw::getAuditTime, LocalDateTime.now())
                .set(Withdraw::getAuditRemark, remark)
                .set(Withdraw::getStatus, 4));
        log.info("[withdraw] 复核拒绝 id={} reviewer={}", withdrawId, reviewer);
        return toVO(getById(withdrawId));
    }

    @Override
    public void send(Long withdrawId) {
        Withdraw w = getById(withdrawId);
        if (w == null) {
            throw new NotFoundException("提现单不存在: " + withdrawId);
        }
        if (w.getStatus() != null && w.getStatus() == 2 && w.getTxHash() == null) {
            Chain chain = chainService.getByChainCode(w.getChainCode());
            Coin coin = coinService.getBySymbol(w.getSymbol());
            try {
                String txHash = broadcast(w, chain, coin);
                this.update(new LambdaUpdateWrapper<Withdraw>()
                        .eq(Withdraw::getId, withdrawId)
                        .set(Withdraw::getTxHash, txHash));
                log.info("[withdraw] 补偿广播成功 id={} txHash={}", withdrawId, txHash);
            } catch (Exception e) {
                unfreezeRollback(withdrawId, "补偿广播失败: " + e.getMessage());
            }
        }
    }

    /** 离线签名 + 广播，返回 txHash。 */
    private String broadcast(Withdraw w, Chain chain, Coin coin) {
        Web3j web3j = chainRegistry.get(chain.getChainCode());
        if (web3j == null) {
            throw new BusinessException("链未注册: " + chain.getChainCode());
        }
        Credentials credentials = walletService.getCredentials();
        String hotAddr = credentials.getAddress();

        BigInteger nonce;
        try {
            nonce = web3j.ethGetTransactionCount(hotAddr, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
        } catch (IOException e) {
            throw new BusinessException("获取 nonce 失败: " + e.getMessage());
        }
        BigInteger gasPrice = clampGasPrice(web3j, chain);
        BigInteger chainId = chain.getChainId() == null ? BigInteger.ONE : BigInteger.valueOf(chain.getChainId());

        // 是否走冷钱包（大额）离线签名
        boolean cold = isColdWithdrawal(w);
        RawTransaction rawTx = buildRawTx(w, chain, coin, nonce, gasPrice);
        if (cold) {
            // 冷钱包：构建 unsigned tx 并存待签（不广播），返回标记
            return prepareColdSign(w, rawTx, chainId);
        }

        // 热钱包：在线签名并广播
        byte[] signed = TransactionEncoder.signMessage(rawTx, chainId.longValue(), credentials);
        if (!walletService.verifySigner(signed, rawTx, chainId.longValue())) {
            throw new BusinessException("签名自检失败，签名与热钱包私钥不符");
        }
        String rawHex = Numeric.toHexString(signed);
        return doBroadcast(web3j, rawHex);
    }

    /** 是否走冷钱包（提现金额 >= 阈值）。 */
    private boolean isColdWithdrawal(Withdraw w) {
        Long threshold = chainProperties.getWithdraw().getColdThreshold();
        return threshold != null && w.getRealAmount() != null && w.getRealAmount() >= threshold;
    }

    /** 构建 RawTransaction（共用：热钱包签名 与 冷钱包待签）。 */
    private RawTransaction buildRawTx(Withdraw w, Chain chain, Coin coin, BigInteger nonce, BigInteger gasPrice) {
        boolean token = "TOKEN".equalsIgnoreCase(coin.getCoinType());
        BigInteger gasLimit = token ? BigInteger.valueOf(100_000) : BigInteger.valueOf(21_000);
        boolean nft = "ERC-721".equalsIgnoreCase(coin.getTokenStandard())
                || "ERC-1155".equalsIgnoreCase(coin.getTokenStandard());
        if (token && nft) {
            String tokenId = w.getTokenId() == null ? "0" : w.getTokenId();
            Function function = new Function("transferFrom",
                    Arrays.asList(new Address(hotWalletAddr(w, chain)), new Address(w.getToAddress()), new Uint256(new BigInteger(tokenId))),
                    List.of());
            String data = FunctionEncoder.encode(function);
            return RawTransaction.createTransaction(nonce, gasPrice, BigInteger.valueOf(150_000),
                    coin.getContractAddress(), BigInteger.ZERO, data);
        } else if (token) {
            Function function = new Function("transfer",
                    Arrays.asList(new Address(w.getToAddress()), new Uint256(BigInteger.valueOf(w.getRealAmount()))),
                    List.of());
            String data = FunctionEncoder.encode(function);
            return RawTransaction.createTransaction(nonce, gasPrice, gasLimit,
                    coin.getContractAddress(), BigInteger.ZERO, data);
        } else {
            return RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit,
                    w.getToAddress(), BigInteger.valueOf(w.getRealAmount()));
        }
    }

    /** 冷钱包待签地址（此处用热钱包地址占位为转出方；无本机冷私钥，离线签名由外部完成）。 */
    private String hotWalletAddr(Withdraw w, Chain chain) {
        try {
            return walletService.getCredentials().getAddress();
        } catch (Exception e) {
            return "0x0000000000000000000000000000000000000000";
        }
    }

    /** 冷钱包流程：构建 unsigned tx 存为待签（状态 6），不广播。返回占位标记。 */
    private String prepareColdSign(Withdraw w, RawTransaction rawTx, BigInteger chainId) {
        String unsignedHex = Numeric.toHexString(TransactionEncoder.encode(rawTx));
        // 对 raw 做签名哈希（keccak），供离线环境校验待签内容一致性
        String signHash = Hash.sha3(unsignedHex);
        int required = Math.max(1, chainProperties.getWithdraw().getColdRequiredSigns());
        this.update(new LambdaUpdateWrapper<Withdraw>()
                .eq(Withdraw::getId, w.getId())
                .set(Withdraw::getStatus, 6)              // 6=待冷钱包签名
                .set(Withdraw::getSignMode, 2)
                .set(Withdraw::getColdTxData, unsignedHex)
                .set(Withdraw::getColdSignHash, signHash)
                .set(Withdraw::getColdSignCount, 0)
                .set(Withdraw::getColdRequiredSigns, required));
        log.info("[withdraw] 进入冷钱包待签 id={} signHash={} required={}",
                w.getId(), signHash, required);
        // 返回占位（非真实 txHash），调用方据此判断不走广播
        return "COLD_PENDING_" + w.getId();
    }

    /** 冷钱包多签：提交一个离线签名（N 审 1 签），达到阈值后广播。 */
    @Override
    public WithdrawVO submitColdSignature(Long withdrawId, String signedRawHex) {
        Withdraw w = getById(withdrawId);
        if (w == null) {
            throw new NotFoundException("提现单不存在: " + withdrawId);
        }
        if (w.getStatus() == null || w.getStatus() != 6) {
            throw new BusinessException("当前状态不可提交冷签名: status=" + w.getStatus());
        }
        int required = w.getColdRequiredSigns() == null ? 1 : w.getColdRequiredSigns();
        int count = w.getColdSignCount() == null ? 0 : w.getColdSignCount();
        int newCount = count + 1;
        this.update(new LambdaUpdateWrapper<Withdraw>()
                .eq(Withdraw::getId, withdrawId)
                .set(Withdraw::getColdSignedRaw, signedRawHex)
                .set(Withdraw::getColdSignCount, newCount));
        if (newCount < required) {
            log.info("[withdraw] 冷签名收集 {}/{} id={}", newCount, required, withdrawId);
            return toVO(getById(withdrawId));
        }
        // 达到阈值：广播最后一份离线签名
        // 注：生产应在离线签名工具中校验签名的 from 地址属于冷钱包白名单；
        //     此处信任离线工具提供的已签名 raw，按多签日志留痕。
        Chain chain = chainService.getByChainCode(w.getChainCode());
        Web3j web3j = chainRegistry.get(chain.getChainCode());
        if (web3j == null) {
            throw new BusinessException("链未注册: " + chain.getChainCode());
        }
        String txHash = doBroadcast(web3j, signedRawHex);
        this.update(new LambdaUpdateWrapper<Withdraw>()
                .eq(Withdraw::getId, withdrawId)
                .set(Withdraw::getStatus, 3)     // 成功
                .set(Withdraw::getTxHash, txHash));
        log.info("[withdraw] 冷钱包广播成功 id={} txHash={}", withdrawId, txHash);
        return toVO(getById(withdrawId));
    }

    /** 冷钱包：待签名提现清单（status=6 的待离线签名大额提现）。 */
    @Override
    public List<WithdrawVO> listColdPending() {
        return this.lambdaQuery()
                .eq(Withdraw::getStatus, 6)
                .orderByAsc(Withdraw::getId)
                .list().stream().map(this::toVO).toList();
    }

    /** 广播已签名 raw tx，返回 txHash。 */
    private String doBroadcast(Web3j web3j, String rawHex) {
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
        return txHash;
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
            return BigInteger.valueOf(1_000_000_000L); // 兜底 1e9 wei
        }
    }

    @Override
    public void confirm(Long withdrawId) {
        Withdraw w = getById(withdrawId);
        if (w == null || w.getStatus() == null || w.getStatus() != 2 || w.getTxHash() == null) {
            return;
        }
        Chain chain = chainService.getByChainCode(w.getChainCode());
        Web3j web3j = chainRegistry.get(chain.getChainCode());
        if (web3j == null) {
            return;
        }
        try {
            var receiptOpt = web3j.ethGetTransactionReceipt(w.getTxHash()).send().getTransactionReceipt();
            if (receiptOpt.isEmpty()) {
                return; // 尚未出块，下一轮再查
            }
            String statusHex = receiptOpt.get().getStatus();
            if ("0x1".equalsIgnoreCase(statusHex)) {
                transferSuccess(w);
            } else if ("0x0".equalsIgnoreCase(statusHex)) {
                unfreezeRollback(withdrawId, "链上交易失败 status=0x0");
            }
        } catch (Exception e) {
            log.warn("[withdraw] 确认查询异常 id={}: {}", withdrawId, e.getMessage());
        }
    }

    /** 成功确认：transfer（冻结 → 平台热钱包账户）→ status=3。 */
    private void transferSuccess(Withdraw w) {
        Long platformUserId = chainProperties.getHotWallet().getPlatformUserId();
        if (platformUserId == null) {
            log.warn("[withdraw] 未配置热钱包平台用户ID，跳过扣减 id={}", w.getId());
            return;
        }
        try {
            // 平台热钱包账户幂等开户
            assetClient.open(platformUserId, w.getSymbol());
            TransferRequest tr = new TransferRequest();
            tr.setRequestId(w.getRequestId() + ":W");
            tr.setFromUserId(w.getUserId());
            tr.setToUserId(platformUserId);
            tr.setSymbol(w.getSymbol());
            tr.setAmount(w.getAmount());
            tr.setBizType("WITHDRAW");
            tr.setRefNo(String.valueOf(w.getId()));
            tr.setRemark("提现扣减");
            Result<LedgerVO> r = assetClient.transfer(tr);
            if (r != null && r.isSuccess()) {
                boolean updated = this.update(new LambdaUpdateWrapper<Withdraw>()
                        .eq(Withdraw::getId, w.getId())
                        .eq(Withdraw::getStatus, 2)
                        .set(Withdraw::getStatus, 3));
                log.info("[withdraw] 成功扣减 id={} 平台+{} symbol={} updated={}",
                        w.getId(), tr.getAmount(), tr.getSymbol(), updated);
            } else {
                log.warn("[withdraw] transfer 未成功 id={} resp={}，保留 status=2 重试", w.getId(), r);
            }
        } catch (Exception e) {
            log.warn("[withdraw] 成功扣减异常 id={}: {}，保留 status=2 重试", w.getId(), e.getMessage());
        }
    }

    /** 失败回滚：unfreeze（冻结 → 可用）→ status=5。 */
    private void unfreezeRollback(Long withdrawId, String reason) {
        Withdraw w = getById(withdrawId);
        if (w == null) {
            return;
        }
        try {
            UnfreezeRequest u = new UnfreezeRequest();
            u.setRequestId(w.getRequestId() + ":U");
            u.setUserId(w.getUserId());
            u.setSymbol(w.getSymbol());
            u.setAmount(w.getAmount());
            u.setBizType("UNFREEZE");
            u.setRefNo(String.valueOf(withdrawId));
            u.setRemark("提现失败回滚");
            Result<LedgerVO> r = assetClient.unfreeze(u);
            if (r != null && r.isSuccess()) {
                boolean updated = this.update(new LambdaUpdateWrapper<Withdraw>()
                        .eq(Withdraw::getId, withdrawId)
                        .eq(Withdraw::getStatus, 2)
                        .set(Withdraw::getStatus, 5)
                        .set(Withdraw::getFailReason, reason));
                log.info("[withdraw] 失败回滚成功 id={} reason={} updated={}", withdrawId, reason, updated);
            } else {
                log.warn("[withdraw] unfreeze 未成功 id={} resp={}，保留 status=2 重试", withdrawId, r);
            }
        } catch (Exception e) {
            log.warn("[withdraw] 失败回滚异常 id={}: {}，保留 status=2 重试", withdrawId, e.getMessage());
        }
    }

    private void failRollback(Long withdrawId, String reason) {
        this.update(new LambdaUpdateWrapper<Withdraw>()
                .eq(Withdraw::getId, withdrawId)
                .in(Withdraw::getStatus, 2)
                .set(Withdraw::getStatus, 5)
                .set(Withdraw::getFailReason, reason));
        log.warn("[withdraw] 处理中止(未上链) id={} reason={}", withdrawId, reason);
    }

    @Override
    public void confirmPending() {
        List<Withdraw> pending = list(new LambdaQueryWrapper<Withdraw>()
                .eq(Withdraw::getStatus, 2)
                .isNotNull(Withdraw::getTxHash)
                .last("limit 200"));
        for (Withdraw w : pending) {
            try {
                confirm(w.getId());
            } catch (Exception e) {
                log.warn("[withdraw] confirmPending 异常 id={}: {}", w.getId(), e.getMessage());
            }
        }
    }

    @Override
    public WithdrawVO getWithdraw(Long withdrawId) {
        Withdraw w = getById(withdrawId);
        if (w == null) {
            throw new NotFoundException("提现单不存在: " + withdrawId);
        }
        return toVO(w);
    }

    @Override
    public Page<WithdrawVO> pageByUser(Long userId, int page, int size) {
        Page<Withdraw> p = this.page(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<Withdraw>()
                        .eq(Withdraw::getUserId, userId)
                        .orderByDesc(Withdraw::getId));
        Page<WithdrawVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    private WithdrawVO toVO(Withdraw w) {
        WithdrawVO vo = new WithdrawVO();
        vo.setId(w.getId());
        vo.setRequestId(w.getRequestId());
        vo.setUserId(w.getUserId());
        vo.setSymbol(w.getSymbol());
        vo.setChainCode(w.getChainCode());
        vo.setToAddress(w.getToAddress());
        vo.setAmount(w.getAmount());
        vo.setTokenId(w.getTokenId());
        vo.setFee(w.getFee());
        vo.setRealAmount(w.getRealAmount());
        vo.setStatus(w.getStatus());
        vo.setAuditBy(w.getAuditBy());
        vo.setAuditTime(w.getAuditTime());
        vo.setAuditRemark(w.getAuditRemark());
        vo.setFreezeLedgerId(w.getFreezeLedgerId());
        vo.setTxHash(w.getTxHash());
        vo.setFailReason(w.getFailReason());
        vo.setCreateTime(w.getCreateTime());
        return vo;
    }
}
