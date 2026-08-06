package com.web3.exchange.common.model;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页数据载体（跨模块复用，供对外分页接口返回）。
 * <p>字段与前端 el-pagination / MyBatis-Plus Page 对齐：total/current/size/pages/records。</p>
 */
@Data
@Schema(description = "分页数据")
public class PageData<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "总记录数")
    private long total;
    @Schema(description = "当前页")
    private long current;
    @Schema(description = "每页条数")
    private long size;
    @Schema(description = "总页数")
    private long pages;
    @Schema(description = "数据列表")
    private List<T> records;

    public PageData() {
    }

    /**
     * 由 MyBatis-Plus Page 转换（records 直接复用）。
     */
    public static <T> PageData<T> of(Page<T> page) {
        PageData<T> data = new PageData<>();
        data.setTotal(page.getTotal());
        data.setCurrent(page.getCurrent());
        data.setSize(page.getSize());
        data.setPages(page.getPages());
        data.setRecords(page.getRecords());
        return data;
    }
}
