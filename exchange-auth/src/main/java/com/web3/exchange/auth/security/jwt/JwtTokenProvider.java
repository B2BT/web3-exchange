package com.web3.exchange.auth.security.jwt;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Slf4j：帮你自动生成一个log日志对象，让你不用自己写Logger，用于Token生成
 * @Component:交给Spring管理，任何地方都可以@Autowired 注入
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    // HS512 = HMAC-SHA512 对称签名，性能高，适合高并发 API
    private static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    // 生成 Access Token
    public String generateAccessToken(String username, Collection<? extends GrantedAuthority> authorities) {
        HashMap<String, Object> claims = new HashMap<>();
        // 区分refresh token
        claims.put("type", "access");
        // authorities -> Spring Security直接可用
        claims.put("authorities", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        return generateToken(username, claims, accessTokenExpiration);

    }

    // 生成 Refresh Token，只用来交换Access Token，可以不放authorities
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return generateToken(username, claims, refreshTokenExpiration);
    }

    // Token生成逻辑
    private String generateToken(String username, Map<String, Object> claims, Long expiration) {
        try {
            // 创建签名器
            JWSSigner signer = new MACSigner(jwtSecret.getBytes());

            JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder()
                    // 用户唯一标识
                    .subject(username)
                    // 签发者
                    .issuer("auth-service")
                    // 签发时间
                    .issueTime(new Date())
                    // 过期时间
                    .expirationTime(new Date(System.currentTimeMillis() + expiration))
                    // Token唯一ID，为Redis黑名单预留
                    .claim("jti", UUID.randomUUID().toString());
            // claims 自定义业务数据
            claims.forEach(claimsSetBuilder::claim);
            JWTClaimsSet claimsSet = claimsSetBuilder.build();

            // 签名 + 序列化:最终返回的是：xxxxx.yyyyy.zzzzz
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claimsSet);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("生成JWT失败", e);
            throw new RuntimeException("生成Token失败", e);
        }
    }

    // 校验Token:签名是否正确，是否过期
    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            MACVerifier macVerifier = new MACVerifier(jwtSecret.getBytes());
            return signedJWT.verify(macVerifier) &&
                    !signedJWT.getJWTClaimsSet().getExpirationTime().before(new Date());
        } catch (Exception e) {
            log.error("Token校验失败：{}", e.getMessage());
            return false;
        }
    }

    // 从Token获取用户名
    public String getUsernameFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            log.error("从Token获取用户名失败", e);
            return null;
        }

    }

    // 获取token类型
    public String getTokenType(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getClaim("type").toString();
        } catch (Exception e) {
            log.error("从Token获取类型失败", e);
            return null;
        }
    }

    // 获取权限列表
    public List<String> getAuthoritiesFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            Object authorities = signedJWT.getJWTClaimsSet().getClaim("authorities");
            if (authorities instanceof List) {
                return (List<String>) authorities;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // 获取JWT Decoder:给Spring Security使用：用于解析JWT，校验签名，校验过期，转换成authentication
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA512");
        return NimbusJwtDecoder.withSecretKey(secretKeySpec)
                .macAlgorithm(JWT_ALGORITHM).build();
    }
}
