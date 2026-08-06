package com.web3.exchange.chain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.chain.dto.DepositVO;
import com.web3.exchange.chain.entity.AssetAddress;
import com.web3.exchange.chain.entity.Chain;
import com.web3.exchange.chain.entity.Coin;
import com.web3.exchange.chain.entity.Deposit;
import com.web3.exchange.chain.feign.AssetClient;
import com.web3.exchange.chain.mapper.AssetAddressMapper;
import com.web3.exchange.chain.mapper.DepositMapper;
import com.web3.exchange.chain.service.DepositService;
import com.web3.exchange.common.asset.dto.CreditRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 充值服务实现。
 * <p>双保险幂等：①t_deposit.uk_tx_hash 防同一笔链上交易重复落单/重复入账；②credit requestId
 * （=DEP:{depositId}）唯一索引防 Feign 重试重复入账。状态机 0=监听中→1=待确认→2=已入账/3=失败。</p>
 */
@Slf4j
@Service
public class DepositServiceImpl extends ServiceImpl<DepositMapper, Deposit> implements DepositService {

    private final AssetAddressMapper assetAddressMapper;
    private final AssetClient assetClient;

    public DepositServiceImpl(AssetAddressMapper assetAddressMapper, AssetClient assetClient) {
        this.assetAddressMapper = assetAddressMapper;
        this.assetClient = assetClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTransfer(Chain chain, Coin coin, String fromAddress, String toAddress,
                               long amount, String txHash, long blockHeight) {
        // 1. 命中归属用户
        AssetAddress addr = findActiveAddress(chain.getChainCode(), toAddress);
        if (addr == null || !addr.getSymbol().equalsIgnoreCase(coin.getSymbol())) {
            return;
        }
        if (coin.getDepositEnabled() == null || coin.getDepositEnabled() != 1) {
            return;
        }
        // 2. 幂等落单（uk_tx_hash 防重）
        long id = IdWorker.getId();
        Deposit d = new Deposit()
                .setRequestId("DEP:" + id)
                .setUserId(addr.getUserId())
                .setCoinId(coin.getId())
                .setSymbol(coin.getSymbol())
                .setChainCode(chain.getChainCode())
                .setFromAddress(fromAddress)
                .setToAddress(toAddress)
                .setAmount(amount)
                .setFee(0L)
                .setTxHash(txHash)
                .setBlockHeight(blockHeight)
                .setConfirmations(0)
                .setRequiredConfirmations(chain.getConfirmations())
                .setStatus(0)
                .setRemark("充值监听");
        d.setId(id);
        try {
            this.save(d);
            log.info("[deposit] 落单 tx={} user={} symbol={} amount={} block={}",
                    txHash, addr.getUserId(), coin.getSymbol(), amount, blockHeight);
        } catch (DuplicateKeyException e) {
            // 已存在：仅更新确认数，不重复入账
            log.debug("[deposit] 重复 tx={}，仅更新确认数", txHash);
        }
    }

    @Override
    public List<Deposit> confirmAndCredit(Chain chain, long latestBlock) {
        List<Deposit> credited = new ArrayList<>();
        List<Deposit> pending = list(new LambdaQueryWrapper<Deposit>()
                .eq(Deposit::getChainCode, chain.getChainCode())
                .in(Deposit::getStatus, 0, 1));
        for (Deposit d : pending) {
            long conf = Math.max(0, latestBlock - d.getBlockHeight() + 1);
            boolean reachable = conf >= d.getRequiredConfirmations();
            if (!reachable) {
                updateConfirmations(d, conf, 0);
                continue;
            }
            // 达标 → 入账（credit 幂等）
            boolean ok = tryCredit(chain, d);
            if (ok) {
                credited.add(d);
            } else {
                updateConfirmations(d, conf, 1); // 保留待确认，下一轮重试
            }
        }
        return credited;
    }

    /**
     * 调 asset credit 入账。requestId=DEP:{id} 幂等。成功 → status=2 + ledger_id；失败 → 保留 status=1 重试。
     */
    private boolean tryCredit(Chain chain, Deposit d) {
        CreditRequest req = new CreditRequest();
        req.setRequestId("DEP:" + d.getId());
        req.setUserId(d.getUserId());
        req.setSymbol(d.getSymbol());
        req.setAmount(d.getAmount());
        req.setBizType("DEPOSIT");
        req.setRefNo(d.getId().toString());
        req.setRemark("充值入账");
        try {
            Result<LedgerVO> r = assetClient.credit(req);
            if (r != null && r.isSuccess() && r.getData() != null) {
                LambdaUpdateWrapper<Deposit> uw = new LambdaUpdateWrapper<>();
                uw.eq(Deposit::getId, d.getId())
                        .eq(Deposit::getStatus, 1)
                        .set(Deposit::getStatus, 2)
                        .set(Deposit::getLedgerId, r.getData().getId())
                        .set(Deposit::getRequestId, req.getRequestId())
                        .set(Deposit::getConfirmations, (int) (chain.getConfirmations() != null ? chain.getConfirmations() : 0));
                boolean updated = this.update(uw);
                if (updated) {
                    log.info("[deposit] 入账成功 depositId={} ledgerId={} user={} symbol={} amount={}",
                            d.getId(), r.getData().getId(), d.getUserId(), d.getSymbol(), d.getAmount());
                    return true;
                }
                return false;
            }
            log.warn("[deposit] credit 未成功 depositId={} resp={}", d.getId(), r);
        } catch (Exception e) {
            log.warn("[deposit] credit 异常 depositId={}: {}", d.getId(), e.getMessage());
        }
        return false;
    }

    private void updateConfirmations(Deposit d, long conf, int status) {
        LambdaUpdateWrapper<Deposit> uw = new LambdaUpdateWrapper<>();
        uw.eq(Deposit::getId, d.getId())
                .set(Deposit::getConfirmations, (int) conf)
                .set(Deposit::getStatus, status);
        this.update(uw);
    }

    @Override
    public AssetAddress findActiveAddress(String chainCode, String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase(Locale.ROOT);
        return assetAddressMapper.selectOne(new LambdaQueryWrapper<AssetAddress>()
                .eq(AssetAddress::getChainCode, chainCode)
                .apply("LOWER(address) = {0}", lower)
                .eq(AssetAddress::getAddressType, 1)
                .eq(AssetAddress::getIsActive, 1)
                .last("limit 1"));
    }

    @Override
    public AssetAddress getDepositAddress(Long userId, String chainCode, String symbol) {
        return assetAddressMapper.selectOne(new LambdaQueryWrapper<AssetAddress>()
                .eq(AssetAddress::getUserId, userId)
                .eq(AssetAddress::getChainCode, chainCode)
                .eq(AssetAddress::getSymbol, symbol)
                .eq(AssetAddress::getAddressType, 1)
                .eq(AssetAddress::getIsActive, 1)
                .last("limit 1"));
    }

    @Override
    public Page<DepositVO> pageByUser(Long userId, int page, int size) {
        Page<Deposit> p = this.page(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<Deposit>()
                        .eq(Deposit::getUserId, userId)
                        .orderByDesc(Deposit::getId));
        Page<DepositVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public Deposit getByTxHash(String txHash) {
        return getOne(new LambdaQueryWrapper<Deposit>()
                .eq(Deposit::getTxHash, txHash)
                .last("limit 1"), false);
    }

    private DepositVO toVO(Deposit d) {
        DepositVO vo = new DepositVO();
        vo.setId(d.getId());
        vo.setUserId(d.getUserId());
        vo.setSymbol(d.getSymbol());
        vo.setChainCode(d.getChainCode());
        vo.setFromAddress(d.getFromAddress());
        vo.setToAddress(d.getToAddress());
        vo.setAmount(d.getAmount());
        vo.setFee(d.getFee());
        vo.setTxHash(d.getTxHash());
        vo.setBlockHeight(d.getBlockHeight());
        vo.setConfirmations(d.getConfirmations());
        vo.setRequiredConfirmations(d.getRequiredConfirmations());
        vo.setLedgerId(d.getLedgerId());
        vo.setStatus(d.getStatus());
        vo.setRemark(d.getRemark());
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }
}
