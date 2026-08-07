package com.web3.exchange.chain.controller;

import com.web3.exchange.chain.dto.WalletBalanceVO;
import com.web3.exchange.chain.dto.WalletCreateRequest;
import com.web3.exchange.chain.dto.WalletImportRequest;
import com.web3.exchange.chain.dto.WalletSendRequest;
import com.web3.exchange.chain.dto.WalletSendResultVO;
import com.web3.exchange.chain.dto.WalletVO;
import com.web3.exchange.chain.entity.UserWallet;
import com.web3.exchange.chain.service.UserWalletService;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自托管钱包<b>对外</b> REST 接口（/api/chain/wallet/**，经网关路由，需登录）。
 * <p>创建/导入/列表/地址/余额。私钥与助记词仅加密入库，绝不回传明文。</p>
 */
@RestController
@RequestMapping("/api/chain/wallet")
@Tag(name = "自托管钱包接口")
public class WalletController {

    private final UserWalletService userWalletService;

    public WalletController(UserWalletService userWalletService) {
        this.userWalletService = userWalletService;
    }

    @Operation(summary = "创建自托管钱包（返回助记词一次）")
    @PostMapping("/create")
    public Result<WalletVO> create(@Valid @RequestBody WalletCreateRequest req) {
        return Result.success(userWalletService.create(req));
    }

    @Operation(summary = "导入自托管钱包（助记词或私钥）")
    @PostMapping("/import")
    public Result<WalletVO> importWallet(@Valid @RequestBody WalletImportRequest req) {
        return Result.success(userWalletService.importWallet(req));
    }

    @Operation(summary = "用户钱包列表")
    @GetMapping("/list")
    public Result<List<WalletVO>> list(@RequestParam("userId") Long userId) {
        return Result.success(userWalletService.listByUser(userId));
    }

    @Operation(summary = "钱包详情/地址")
    @GetMapping("/{id}/address")
    public Result<WalletVO> address(@RequestParam("userId") Long userId,
                                    @PathVariable("id") Long id) {
        UserWallet w = userWalletService.getById(userId, id);
        WalletVO vo = new WalletVO();
        vo.setId(w.getId());
        vo.setUserId(w.getUserId());
        vo.setChainCode(w.getChainCode());
        vo.setWalletType(w.getWalletType());
        vo.setAddress(w.getAddress());
        vo.setAddressType(w.getAddressType());
        vo.setName(w.getName());
        vo.setStatus(w.getStatus());
        return Result.success(vo);
    }

    @Operation(summary = "钱包链上余额")
    @GetMapping("/{id}/balance")
    public Result<List<WalletBalanceVO>> balance(@RequestParam("userId") Long userId,
                                                 @PathVariable("id") Long id) {
        return Result.success(userWalletService.balance(userId, id));
    }

    @Operation(summary = "钱包链上转账（离线签名 + 广播）")
    @PostMapping("/{id}/send")
    public Result<WalletSendResultVO> send(@RequestParam("userId") Long userId,
                                           @PathVariable("id") Long id,
                                           @RequestBody WalletSendRequest req) {
        return Result.success(userWalletService.send(userId, id, req));
    }
}
