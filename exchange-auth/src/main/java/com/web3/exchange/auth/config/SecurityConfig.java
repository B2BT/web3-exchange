package com.web3.exchange.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

/**
 * @EnableWebSecurity：开启SpringSecurity Web安全过滤体系；
 * 背后：注册了SecurityFilterChain；启用一整条Servlet Filter链，所有HTTP请求都会先过Security
 * 请求
 * → JWT Filter
 * → Authentication Filter
 * → Authorization Filter
 * → Controller
 * @EnableMethodSecurity(prePostEnabled = true)：启动方法级别权限控制：
 * 启用了什么能力？
 * •	@PreAuthorize
 * •	@PostAuthorize
 * •	@PreFilter
 * •	@PostFilter
 * 举例：
 * @PreAuthorize("hasAuthority('user:view')")
 * @GetMapping("/admin/users") public List<User> listUsers() {
 * return userService.list();
 * }
 * @RequiredArgsConstructor:Lombok提供的注解，为所有final字段生成构造方法
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    // 把JWT里的内容->Spring Security能识别的权限
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Value("${jwt.secret}")
    private String secret;

    /**
     * 定义过滤链的安全规则：
     * .csrf:关闭CSRF（防止浏览器Cookie自动携带的攻击）JWT项目必须关
     * .cors:启动CORS跨域，允许前端跨域访问
     * .sessionManagement：Session策略：无状态，不要创建HttpSession
     * .authorizeHttpRequests：接口白名单访问。
     * .requestMatchers().permitAll()：白名单接口，无需登录
     * .anyRequest().authenticated()：其余接口，必须认证
     * .oauth2ResourceServer:OAuth2 Resource Server + JWT（核心）
     *
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/captcha",
                                "/api/auth/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                );
        return http.build();
    }

    /**
     * 允许任意来源的前端，以任意方法、任意请求头、跨域访问你的后端接口（不带Cookie）
     * 用于开发环境
     * 生产环境
     * corsConfiguration.setAllowedOrigins(
     * List.of(
     * "http://localhost:8000",
     * "https://admin.xxx.com"
     * )
     * );
     *
     * @return
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 跨域配置对象
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        // 允许任意来源（Origins）访问，任何网站都能发请求
        corsConfiguration.setAllowedOrigins(Arrays.asList("*"));
        // 允许所有HTTP方法，包括GET、POST、PUT、DELETE、OPTIONS（预检）
        corsConfiguration.setAllowedMethods(Arrays.asList("*"));
        // 允许任意请求头，Authorization（JWT）、Content-Type、X-Requested-With、自定义 Header
        corsConfiguration.setAllowedHeaders(Arrays.asList("*"));
        // 不允许携带Cookie/Authorization等浏览器凭证，但是不影响手动加的Authorization Header
        corsConfiguration.setAllowCredentials(false);

        // 注册跨域规则路径，对所有接口路径生效
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    /**
     * 负责密码的加密 & 校验
     * •	注册时：明文 → 加密存库
     * •	登录时：明文 + 数据库存的 hash → 比对
     * 为什么用 BCryptPasswordEncoder？
     * 这是 Spring Security 官方默认 & 推荐
     * 它的特点：
     * •	✅ 自带随机盐（防彩虹表）
     * •	✅ 可调计算强度（防暴力破解）
     * •	✅ 不可逆（安全）
     * 缺失导致登录永远失败
     *
     * @return
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证流程调度器，负责：给一个Authentication，告诉能不能通过；
     * 用于管理后台账号密码登录，内部系统登录，备用登录方式
     * ⚠️ 很多人会混淆：
     * •	AuthenticationManager 登录时用
     * •	JwtDecoder 请求鉴权时用
     * 它们不是一回事。
     * 缺失导致自定义接口无法认证
     *
     * @param authenticationConfiguration
     * @return
     * @throws Exception
     */
    @Bean
    public AuthenticationManager authenticationManagerBean(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 验证JWT是否 被篡改、已过期、签名合法
     * 验证失败提示401，进不了controller
     * 缺失导致所有JWT请求401
     *
     * @return
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String secret = this.secret;
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

}
