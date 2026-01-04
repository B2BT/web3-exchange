package com.web3.exchange.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

/**
 * Spring Security核心配置类
 * // @EnableWebSecurity 激活Web安全配置：告诉Spring我要定义Web层安全规则了，
 * 会自动导入SpringSecuriy核心配置类，允许通过SecurityFilterChain来控制URL那些需要拦截，哪些需要放行。
 * 不加会拦截所有请求并生成一个默认user密码
 * // @EnableMethodSecurity，激活方法级别的安全控制（Controller或者Service），
 * 取代了@EnableGlobalMethodSecurity(SpringBoot启动类注解)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {


    /**
     * 请求白名单
     */
    private static final String[] WHITE_LIST = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/captcha",
            "/actuator/health"
    };

    /**
     * Security 配置核心
     * 禁用Session，所有请求走JWT
     *
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（跨站请求伪造）的防护。因为项目采用JWT且Session的策略为STATELESS,
                // 客户端不通过cookie存储SessionID，因此不受CSRF攻击威胁，禁用可简化开发
                .csrf(AbstractHttpConfigurer::disable)
                // 配置跨域资源共享。允许前端从不同的域名和端口访问后端接口
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 设置会话管理策略为无状态STATELESS。
                // Spring Security不会创建使用任何HttpSession，每次请求必须携带Token(JWT)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 定义接口访问权限。登陆、刷新Token、验证码以及健康检查接口设为白名单，无需鉴权。
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST).permitAll()
                        .anyRequest().authenticated()
                )
                // 将后端作为OAuth2资源服务器。配置了JWT校验，并使用了转换器将JWT中的Claim解析成
                // Spring Security的 GrantedAuthority（权限/角色）
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> oauth2
                                .jwt().jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );
        // todo 完善异常处理
        return http.build();
    }

    /**
     * 定义了CORS跨域资源共享的配置源。
     * 告诉服务器如何处理来自不同域名、端口或协议的请求。
     * 2026/1/3开发环境全放开配置
     * 生产环境精确限制Origin
     * 优化：仅允许信任的域名访问
     * configuration.setAllowedOrigins(Arrays.asList("https://www.yourdomain.com", "http://localhost:5173"));
     *
     * @return
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许来自任何域名的跨域请求
        configuration.setAllowedOrigins(List.of("*"));
        // 允许所有HTTP方法（GET,POST,PUT,DELETE,OPTIONS等）
        configuration.setAllowedMethods(List.of("*"));
        // 允许请求携带任何自定义Header
        configuration.setAllowedHeaders(List.of("*"));
        // 不允许跨域携带Cookie或身份凭证
        configuration.setAllowCredentials(false);

        // 将定义好的规则CORS规则应用到具体的URL路径上
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 从JWT令牌中提取权限并将其映射到Spring Security的权限模型中。
     *
     * @return
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // 将Jwt对象转换成 Spring Security 识别的 Authentication 认证对象
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // 从JWT中提取权限声明
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // 自定义权限前缀
        // 这使得你可以直接在代码中使用 @PreAuthorize("hasRole('ADMIN')")。
        // 如果不改，你必须写成 hasAuthority('SCOPE_ADMIN')
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        // 指定权限字段名：告诉 Spring 去 JWT 的 Payload 中找名为 roles 的 JSON 字段。
        authoritiesConverter.setAuthoritiesClaimName("roles");
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * 安全基础：密码编码器
     * 必须定义一个加密Bean来验证数据库的加密密码
     *
     * @return
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * 认证流程的总指挥官
     * 是Spring Security 提供的执行认证的入口接口。
     * 它的唯一任务是：接收一个包含用户名/密码（或token）的认证请求，并验证其是否合法。
     * 没有他，登陆接口无法手动触发验用户名和密码是否匹配。
     * 有了它，可以在Controller中调用它来完成登陆请求
     * @param authenticationConfiguration
     * @return
     * @throws Exception
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 核心：验证Token签名
     * 配置了如何解析的权限，还需要配置如何验证Token的合法性
     *
     * @return
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // 从配置读取密钥
        String secret = "your-256-bit-secret-key-for-jwt-signing-change-this-in-production";
        SecretKey key = new SecretKeySpec(secret.getBytes(), "HmacSHA512");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}