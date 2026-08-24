package com.web3.exchange.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.risk.entity.AmlBlacklist;
import org.apache.ibatis.annotations.Mapper;

/**
 * AML 黑名单/制裁名单 Mapper。
 */
@Mapper
public interface AmlBlacklistMapper extends BaseMapper<AmlBlacklist> {
}