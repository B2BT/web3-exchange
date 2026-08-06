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
        if (w.getStatus() == null || (w.getStatus() != 0 && w.getStatus() != 1)) {
            throw new BusinessException("当前状态不可审核: status=" + w.getStatus());
        }
        boolean approve = Boolean.TRUE.equals(req.getApprove());
        String remark = req.getRemark();

        // 1. 状态抢占（乐观锁 + status 条件）：0/1 → 处理中(2) 或 拒绝(4)
        LambdaUpdateWrapper<Withdraw> uw = new LambdaUpdateWrapper<>();
        uw.eq(Withdraw::getId, withdrawId)
                .in(Withdraw::getStatus, 0, 1)
                .set(Withdraw::getAuditBy, "admin")
                .set(Withdraw::getAuditTime, LocalDateTime.now())
                .set(Withdraw::getAuditRemark, remark);
        if (approve) {
            uw.set(Withdraw::getStatus, 2);
        } else {
            uw.set(Withdraw::getStatus, 4);
        }
        boolean claimed = this.update(uw);
        if (!claimed) {
            throw new BusinessException("审核并发冲突，请重试");
        }
        if (!approve) {
            log.info("[withdraw] 审核拒绝 id={}", withdrawId);
            return toVO(getById(withdrawId));
        }

        // 2. 冻结（进入处理中前锁定资金）
        Chain chain = chainService.getByChainCode(w.getChainCode());
        Coin coin = coinService.getBySymbol(w.getSymbol());
        FreezeRequest freezeReq = new FreezeRequest();
        freezeReq.setRequestId(w.getRequestId());
        freezeReq.setUserId(w.getUserId());
        freezeReq.setSymbol(w.getSymbol());
        freezeReq.setAmount(w.getAmount());
        freezeReq.setBizType("FREEZE");
        freezeReq.setRefNo(String.valueOf(withdrawId));
        freezeReq.setRemark("提现冻结");
        try {
            Result<LedgerVO> fr = assetClient.freeze(freezeReq);
            if (fr == null || !fr.isSuccess() || fr.getData() == null) {
                failRollback(withdrawId, "冻结失败: " + (fr == null ? "null" : fr.getMessage()));
                return toVO(getById(withdrawId));
            }
            this.update(new LambdaUpdateWrapper<Withdraw>()
                    .eq(Withdraw::getId, withdrawId)
                    .set(Withdraw::getFreezeLedgerId, fr.getData().getId()));
            log.info("[withdraw] 冻结成功 id={} ledgerId={}", withdrawId, fr.getData().getId());
        } catch (Exception e) {
            failRollback(withdrawId, "冻结异常: " + e.getMessage());
            return toVO(getById(withdrawId));
        }

        // 3. 离线签名 + 广播
        try {
            String txHash = broadcast(w, chain, coin);
            this.update(new LambdaUpdateWrapper<Withdraw>()
                    .eq(Withdraw::getId, withdrawId)
                    .set(Withdraw::getTxHash, txHash));
            log.info("[withdraw] 广播成功 id={} txHash={}", withdrawId, txHash);
        } catch (Exception e) {
            log.warn("[withdraw] 广播失败 id={}: {}", withdrawId, e.getMessage());
            unfreezeRollback(withdrawId, "上链失败: " + e.getMessage());
        }
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
        boolean token = "TOKEN".equalsIgnoreCase(coin.getCoinType());
        BigInteger gasLimit = token ? BigInteger.valueOf(100_000) : BigInteger.valueOf(21_000);
        BigInteger chainId = chain.getChainId() == null ? BigInteger.ONE : BigInteger.valueOf(chain.getChainId());

        RawTransaction rawTx;
        if (token) {
            Function function = new Function("transfer",
                    Arrays.asList(new Address(w.getToAddress()), new Uint256(BigInteger.valueOf(w.getRealAmount()))),
                    List.of());
            String data = FunctionEncoder.encode(function);
            rawTx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit,
                    coin.getContractAddress(), BigInteger.ZERO, data);
        } else {
            rawTx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit,
                    w.getToAddress(), BigInteger.valueOf(w.getRealAmount()));
        }

        byte[] signed = TransactionEncoder.signMessage(rawTx, chainId.longValue(), credentials);

        // 签名自检：重签比较 r/s，确认由热钱包私钥签名
        if (!walletService.verifySigner(signed, rawTx, chainId.longValue())) {
            throw new BusinessException("签名自检失败，签名与热钱包私钥不符");
        }

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
