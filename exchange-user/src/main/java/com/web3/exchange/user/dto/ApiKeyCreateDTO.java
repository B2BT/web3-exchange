package com.web3.exchange.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建 API 密钥请求。
 */
@Data
public class ApiKeyCreateDTO {
    /** 备注 */
    private String label;
    /** 权限：READ / READ,TRADE / READ,TRADE,WITHDRAW（逗号分隔） */
    @NotBlank(message = "权限不能为空")
    @Pattern(regexp = "^(READ)(,?(TRADE|WITHDRAW))*$", message = "权限取值不合法")
    private String permission;
}
