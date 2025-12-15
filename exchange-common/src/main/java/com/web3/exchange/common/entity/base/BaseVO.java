package com.web3.exchange.common.entity.base;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础VO
 */
@Data
public abstract class BaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(name = "主键ID")
    private Long id;

    @Schema(name = "创建人")
    private String createBy;

    @Schema(name = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(name = "更新人")
    private String updateBy;

    @Schema(name = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(name = "扩展数据")
    private Object extData;
}