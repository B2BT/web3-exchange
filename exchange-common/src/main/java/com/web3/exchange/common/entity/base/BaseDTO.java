package com.web3.exchange.common.entity.base;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;

/**
 * 基础DTO
 */
@Data
public abstract class BaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(name = "主键ID", example = "123456")
    @NotNull(message = "ID不能为空", groups = {UpdateGroup.class})
    @Positive(message = "ID必须为正数")
    private Long id;

    @Schema(name = "租户ID", hidden = true)
    private Long tenantId;

    @Schema(name = "当前页码", example = "1")
    @PositiveOrZero(message = "页码必须大于等于0")
    private Integer pageNum = 1;

    @Schema(name = "每页大小", example = "10")
    @Positive(message = "每页大小必须大于0")
    private Integer pageSize = 10;

    @Schema(name = "排序字段", example = "createTime")
    private String orderBy = "create_time";

    @Schema(name = "排序方向", example = "DESC")
    private String orderDirection = "DESC";

    /**
     * 验证分组：新增
     */
    public interface CreateGroup {}

    /**
     * 验证分组：更新
     */
    public interface UpdateGroup {}

    /**
     * 验证分组：查询
     */
    public interface QueryGroup {}

    /**
     * 获取偏移量（用于分页查询）
     */
    public Long getOffset() {
        return (long) (pageNum - 1) * pageSize;
    }
}