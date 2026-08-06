package com.web3.exchange.chain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 充币地址视图。
 */
@Data
@Schema(description = "充币地址视图")
public class AssetAddressVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "地址ID")
    private Long id;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "链编码")
    private String chainCode;
    @Schema(description = "币种符号")
    private String symbol;
    @Schema(description = "充币地址")
    private String address;
    @Schema(description = "备注/Tag")
    private String memo;
    @Schema(description = "地址类型")
    private Integer addressType;
    @Schema(description = "是否启用")
    private Integer isActive;
}
