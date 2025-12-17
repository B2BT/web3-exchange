package com.web3.exchange.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI simpleOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("用户服务API")
                        .version("1.0")
                        .description("简单的用户管理API测试")
                );
    }
}