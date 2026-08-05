package com.web3.exchange.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.asset.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    /**
     * 行锁查询：按 user+symbol 锁定账户行（FOR UPDATE），用于资金串行化。
     */
    @Select("SELECT * FROM t_wallet_account WHERE user_id = #{userId} AND symbol = #{symbol} AND is_deleted = 0 FOR UPDATE")
    Account selectByUserAndSymbolForUpdate(@Param("userId") Long userId, @Param("symbol") String symbol);

    /**
     * 行锁查询：按账户ID锁定。
     */
    @Select("SELECT * FROM t_wallet_account WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    Account selectByIdForUpdate(@Param("id") Long id);
}
