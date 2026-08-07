package com.web3.exchange.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.chain.entity.UserWallet;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户自托管钱包 Mapper（t_user_wallet）。
 */
@Mapper
public interface UserWalletMapper extends BaseMapper<UserWallet> {
}
