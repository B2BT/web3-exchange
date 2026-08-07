package com.web3.exchange.margin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 杠杆业务配置（margin.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "margin")
public class MarginProperties {

    /** 平台资金池用户（借币垫付，MVP 简化） */
    private Long platformUserId;

    /** 计息配置 */
    private Interest interest = new Interest();

    /** 强平配置 */
    private Liquidation liquidation = new Liquidation();

    @Data
    public static class Interest {
        /** 计息开关 */
        private boolean enabled = true;
    }

    @Data
    public static class Liquidation {
        /** 强平开关 */
        private boolean enabled = true;
        /** 强平折价率(百分数,95=95%) */
        private int liquidationDiscount = 95;
    }
}
