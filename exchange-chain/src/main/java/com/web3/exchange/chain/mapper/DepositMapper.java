package com.web3.exchange.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.chain.entity.Deposit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 充值记录 Mapper（读/写 asset 库 t_deposit）。
 */
@Mapper
public interface DepositMapper extends BaseMapper<Deposit> {

    /**
     * 取某链 status∈(0,1) 的最大区块高度，用于重启后游标回退补块的起点。
     */
    @Select("SELECT COALESCE(MAX(block_height), 0) FROM t_deposit WHERE chain_code = #{chainCode} AND status IN (0,1) AND is_deleted = 0")
    Long maxBlockHeightForPending(@Param("chainCode") String chainCode);
}
