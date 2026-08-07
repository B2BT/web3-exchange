package com.web3.exchange.user.service;

import com.web3.exchange.user.dto.ApiKeyCreateDTO;
import com.web3.exchange.user.vo.ApiKeyVO;

import java.util.List;

/**
 * 交易 API 密钥服务：创建（明文 Secret 一次返回）/ 列表（脱敏）/ 停启 / 删除。
 */
public interface ApiKeyService {
    ApiKeyVO create(Long userId, ApiKeyCreateDTO dto);
    List<ApiKeyVO> list(Long userId);
    void toggle(Long userId, Long id, boolean enable);
    void delete(Long userId, Long id);
}
