package com.web3.exchange.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.market.entity.KlineRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * K线落库 Mapper（t_kline）。
 * <p>
 * upsert 用 {@code ON DUPLICATE KEY UPDATE}：命中 {@code uk_symbol_period_window} 唯一索引时
 * 只更新 OHLCV（open 保留首个），不产生重复行——同窗口重复消费/重复落库幂等收敛。
 * </p>
 */
@Mapper
public interface KlineRowMapper extends BaseMapper<KlineRow> {

    /** 单行 upsert（已关闭窗口最终态写库）。 */
    @Insert("INSERT INTO t_kline (id, symbol, period, window_start, open, high, low, close, volume, quote_volume, create_time, update_time) "
            + "VALUES (#{id}, #{symbol}, #{period}, #{windowStart}, #{open}, #{high}, #{low}, #{close}, #{volume}, #{quoteVolume}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE high = VALUES(high), low = VALUES(low), close = VALUES(close), "
            + "volume = VALUES(volume), quote_volume = VALUES(quote_volume), version = version + 1, update_time = NOW()")
    int upsert(KlineRow row);

    /** 批量 upsert（循环单条，简单可靠；量级小，足够）。 */
    default int upsertBatch(List<KlineRow> rows) {
        int n = 0;
        for (KlineRow r : rows) {
            n += upsert(r);
        }
        return n;
    }

    /** 按周期取最近 N 条（重启重建内存用），窗口时间倒序限制条数。 */
    @Select("SELECT * FROM t_kline WHERE is_deleted = 0 AND period = #{period} "
            + "ORDER BY window_start DESC LIMIT #{limit}")
    List<KlineRow> selectRecentByPeriod(@Param("period") String period,
                                        @Param("limit") int limit);
}
