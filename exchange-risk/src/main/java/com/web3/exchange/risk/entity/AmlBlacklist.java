package com.web3.exchange.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AML 黑名单/制裁名单条目。
 */
@Data
@TableName("t_aml_blacklist")
public class AmlBlacklist implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** PERSON_NAME / PERSON_ID_CARD / SANCTION_ADDRESS */
    private String matchType;
    private String matchValue;
    private String reason;
    private String source;
    /** 1=生效 0=失效 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}