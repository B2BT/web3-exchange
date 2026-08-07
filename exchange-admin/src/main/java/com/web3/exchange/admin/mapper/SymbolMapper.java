package com.web3.exchange.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.admin.entity.AdminSymbol;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SymbolMapper extends BaseMapper<AdminSymbol> {
}
