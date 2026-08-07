package com.web3.exchange.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 反钓鱼码表（t_anti_phishing）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_anti_phishing")
public class AntiPhishing extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 反钓鱼短语 */
    private String phrase;
}
