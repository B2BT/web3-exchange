package com.web3.exchange.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 网关全局跨域配置（WebFlux 响应式）。
 * <p>
 * 注意：网关是统一入口，浏览器跨域请求（前端 dev server / APP 内置 WebView）先到达网关，
 * 因此 CORS 在此统一配置，各下游服务无需重复处理。
 * 使用 {@link CorsWebFilter} 而非 Spring MVC 的拦截器（网关无 Servlet 栈）。
 */
@Configuration
public class GlobalCorsConfiguration {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // allowCredentials=true 时不允许使用 "*"，必须用具体的来源或模式
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");
        config.addAllowedMethod("*");      // GET/POST/PUT/DELETE/OPTIONS 等全部允许
        config.addAllowedHeader("*");      // Authorization / Content-Type 等全部允许
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);           // 预检结果缓存 1 小时，减少 OPTIONS 请求

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
