package com.web3.exchange.user.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.user.dto.ApiKeyCreateDTO;
import com.web3.exchange.user.service.ApiKeyService;
import com.web3.exchange.user.vo.ApiKeyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 交易 API 密钥管理接口（/api/users/api-keys/**）。
 * <p>对标主流交易所 OpenAPI 密钥：创建返回明文 Secret 一次，列表脱敏，支持停启与删除。</p>
 */
@RestController
@RequestMapping("/api/users/api-keys")
@Tag(name = "交易API密钥", description = "用户级 OpenAPI 密钥管理")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Operation(summary = "创建API密钥（返回明文Secret一次）")
    @PostMapping("/create")
    public Result<ApiKeyVO> create(@RequestHeader("X-User-Id") Long userId,
                                   @Valid @RequestBody ApiKeyCreateDTO dto) {
        return Result.success(apiKeyService.create(userId, dto));
    }

    @Operation(summary = "API密钥列表（Secret脱敏）")
    @GetMapping("/list")
    public Result<List<ApiKeyVO>> list(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(apiKeyService.list(userId));
    }

    @Operation(summary = "停用/启用API密钥")
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable("id") Long id,
                               @RequestParam("enable") boolean enable) {
        apiKeyService.toggle(userId, id, enable);
        return Result.success();
    }

    @Operation(summary = "删除API密钥")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable("id") Long id) {
        apiKeyService.delete(userId, id);
        return Result.success();
    }
}
