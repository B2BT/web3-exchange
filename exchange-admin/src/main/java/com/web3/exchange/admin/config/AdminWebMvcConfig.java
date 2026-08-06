package com.web3.exchange.admin.config;

import com.web3.exchange.admin.interceptor.AdminRoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理平台 WebMvc 配置：为 /api/admin/** 注册 ADMIN 角色鉴权拦截器。
 */
@Configuration
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminRoleInterceptor adminRoleInterceptor;

    public AdminWebMvcConfig(AdminRoleInterceptor adminRoleInterceptor) {
        this.adminRoleInterceptor = adminRoleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
