package com.web3.exchange.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.asset.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    /**
     * <b>行锁查询账户</b>：按 user+symbol 锁定账户行（SELECT ... FOR UPDATE）。
     * <p>
     * 业务原因：资金变动必须串行化，防止同一账户的冻结/解冻/过户/入账并发导致余额错账。
     * 通过数据库行锁将同账户操作串行执行（悲观锁，资金安全第一道防线），
     * 配合 LedgerService.doChange 的乐观锁(version)与唯一索引幂等兜底。
     * <b>必须在事务内调用</b>，否则锁随语句结束即释放。
     * </p>
     */
    @Select("SELECT * FROM t_wallet_account WHERE user_id = #{userId} AND symbol = #{symbol} AND is_deleted = 0 FOR UPDATE")
    Account selectByUserAndSymbolForUpdate(@Param("userId") Long userId, @Param("symbol") String symbol);

    /**
     * <b>行锁查询账户</b>：按账户ID锁定（SELECT ... FOR UPDATE）。
     * <p>用于已知 accountId 的场景下锁定账户行以串行化资金操作；必须在事务内调用。</p>
     */
    @Select("SELECT * FROM t_wallet_account WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    Account selectByIdForUpdate(@Param("id") Long id);
}
