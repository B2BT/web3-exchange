package com.web3.exchange.auth.security.jwt;

import com.web3.exchange.common.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

// JwtAuthenticationFilter.java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. 获取Token
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateAccessToken(jwt)) {
                // 2. 检查Token是否在黑名单
                if (isTokenBlacklisted(jwt)) {
                    log.warn("Token已被加入黑名单: {}", jwt);
                    throw new AuthException("Token无效");
                }

                // 3. 获取用户名
                String username = tokenProvider.getUsernameFromAccessToken(jwt);

                // 4. 加载用户信息
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 5. 创建认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // 6. 设置认证详情
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 7. 设置安全上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 8. 检查Token是否即将过期
                checkTokenExpiringSoon(jwt, response);
            }
        } catch (Exception e) {
            log.error("无法设置用户认证: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isTokenBlacklisted(String token) {
        String key = "token:blacklist:" + token;
        return redisTemplate.hasKey(key);
    }

    private void checkTokenExpiringSoon(String token, HttpServletResponse response) {
        try {
            // 解析Token获取过期时间
            Claims claims = Jwts.parserBuilder()
//                    .setSigningKey(tokenProvider)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Date expiration = claims.getExpiration();
            long timeUntilExpiry = expiration.getTime() - System.currentTimeMillis();

            // 如果5分钟内过期，在响应头中提示
            if (timeUntilExpiry <= 5 * 60 * 1000) {
                response.setHeader("X-Token-Expiring-Soon", "true");
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
    }
}
