package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Ledger;
import com.web3.exchange.common.asset.dto.CreditRequest;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.asset.dto.TransferRequest;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;

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
