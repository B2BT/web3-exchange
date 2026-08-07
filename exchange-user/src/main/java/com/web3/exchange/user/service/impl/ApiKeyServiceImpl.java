package com.web3.exchange.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.web3.exchange.common.exception.NotFoundException;
import com.web3.exchange.user.dto.ApiKeyCreateDTO;
import com.web3.exchange.user.entity.ApiKey;
import com.web3.exchange.user.mapper.ApiKeyMapper;
import com.web3.exchange.user.service.ApiKeyService;
import com.web3.exchange.user.util.AesGcmUtil;
import com.web3.exchange.user.vo.ApiKeyVO;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 交易 API 密钥服务实现。
 * <p>安全要点：secretKey 为 32 字节随机，仅创建时明文返回一次；落库用 AES-GCM 加密；
 * 列表/详情一律脱敏为 ***；删除为逻辑删除；停用后不可用。</p>
 */
@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final String PREFIX = "W3-";
    private static final String MASK = "***";

    private final ApiKeyMapper apiKeyMapper;

    public ApiKeyServiceImpl(ApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    @Override
    public ApiKeyVO create(Long userId, ApiKeyCreateDTO dto) {
        // 生成 apiKey（全局唯一）+ secretKey（32 字节随机）
        String apiKey = PREFIX + UUID.randomUUID().toString().replace("-", "");
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String plainSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        ApiKey k = new ApiKey();
        k.setId(IdWorker.getId());
        k.setUserId(userId);
        k.setApiKey(apiKey);
        k.setSecretKey(AesGcmUtil.encrypt(plainSecret)); // 加密落库
        k.setLabel(dto.getLabel() == null ? "" : dto.getLabel().trim());
        k.setPermission(dto.getPermission());
        k.setStatus(1);
        apiKeyMapper.insert(k);

        ApiKeyVO vo = toVO(k);
        vo.setSecretKey(plainSecret); // 仅此一次明文返回
        return vo;
    }

    @Override
    public List<ApiKeyVO> list(Long userId) {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getUserId, userId)
                        .orderByDesc(ApiKey::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public void toggle(Long userId, Long id, boolean enable) {
        requireOwned(userId, id);
        apiKeyMapper.update(null, new LambdaUpdateWrapper<ApiKey>()
                .eq(ApiKey::getId, id)
                .eq(ApiKey::getUserId, userId)
                .set(ApiKey::getStatus, enable ? 1 : 0));
    }

    @Override
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        apiKeyMapper.delete(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getId, id)
                .eq(ApiKey::getUserId, userId));
    }

    /** 校验归属：key 必须属于该用户，否则抛 NotFound。 */
    private ApiKey requireOwned(Long userId, Long id) {
        ApiKey k = apiKeyMapper.selectById(id);
        if (k == null || !k.getUserId().equals(userId)) {
            throw new NotFoundException("API密钥不存在: " + id);
        }
        return k;
    }

    private ApiKeyVO toVO(ApiKey k) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(k.getId());
        vo.setApiKey(k.getApiKey());
        vo.setSecretKey(MASK); // 默认脱敏
        vo.setLabel(k.getLabel());
        vo.setPermission(k.getPermission());
        vo.setStatus(k.getStatus());
        vo.setLastUsedAt(k.getLastUsedAt());
        vo.setCreateTime(k.getCreateTime());
        return vo;
    }
}
