package com.web3.exchange.margin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.margin.dto.MarginAccountVO;
import com.web3.exchange.margin.dto.MarginBorrowRequest;
import com.web3.exchange.margin.dto.MarginTransferRequest;
import com.web3.exchange.margin.entity.MarginAccount;
import com.web3.exchange.margin.entity.MarginLoan;

/**
 * 杠杆账户服务。
 */
public interface MarginAccountService extends IService<MarginAccount> {

    /** 幂等开户 */
    MarginAccountVO open(Long userId, String symbol);

    /** 抵押入金：现货 available → 杠杆 collateral */
    MarginAccountVO transferIn(MarginTransferRequest req);

    /** 抵押出金：杠杆 collateral → 现货 available */
    MarginAccountVO transferOut(MarginTransferRequest req);

    /** 借币 */
    MarginAccountVO borrow(MarginBorrowRequest req);

    /** 还币（本金 + 利息） */
    MarginAccountVO repay(MarginBorrowRequest req);

    /** 账户详情 */
    MarginAccountVO getAccount(Long userId, String symbol);

    /** 用户借币记录分页 */
    Page<MarginLoan> pageLoans(Long userId, int page, int size);
}
