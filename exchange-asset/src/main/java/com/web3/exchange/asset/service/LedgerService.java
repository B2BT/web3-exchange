package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Ledger;
import com.web3.exchange.common.asset.dto.CreditRequest;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.asset.dto.TransferRequest;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;

/**
 * 资金流水服务——<b>资产域资金变动的唯一入口</b>。
 * <p>
 * 冻结/解冻/过户/充值入账全部收敛于此，内部统一走 doChange：同一本地事务内
 * 「写流水 + 更新余额」，并叠加行锁、乐观锁、幂等三重保障，确保资金不多、不少、
 * 不重复扣减。调用方需先通过 AccountService.lockByUserAndSymbol 行锁锁定账户。
 * </p>
 */
public interface LedgerService extends IService<Ledger> {

    /**
     * 冻结（可用→冻结）。
     */
    LedgerVO freeze(FreezeRequest req);

    /**
     * 解冻（冻结→可用）。
     */
    LedgerVO unfreeze(UnfreezeRequest req);

    /**
     * 过户（from 冻结减少 + 写 TRANSFER_OUT；to 可用增加 + 写 TRANSFER_IN），单事务原子。
     */
    LedgerVO transfer(TransferRequest req);

    /**
     * 充值入账（可用增加，写 DEPOSIT 流水）。
     */
    LedgerVO credit(CreditRequest req);

    /**
     * 分页查流水（对账/审计）。
     */
    Page<LedgerVO> pageLedgers(Long accountId, int page, int size);

    LedgerVO toVO(Ledger ledger);
}
