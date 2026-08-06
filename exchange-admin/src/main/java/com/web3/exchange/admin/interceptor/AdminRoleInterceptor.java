package com.web3.exchange.admin.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.admin.entity.AdminUser;
import com.web3.exchange.admin.mapper.AdminUserMapper;
import com.web3.exchange.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 管理平台角色鉴权拦截器（/api/admin/**）。
 * <p>
 * 网关 AuthFilter 已在请求头注入 X-User-Id（经 JWT 校验的用户ID）；本拦截器据该ID查询
 * t_user.role，仅 role=ADMIN 放行；缺失/非 ADMIN 返回 401/403 统一 Result JSON。
 * 这样无需在网关维护角色白名单，角色判断收敛在管理域、直连数据库，实时生效。
 * </p>
 */
@Slf4j
@Component
public class AdminRoleInterceptor implements HandlerInterceptor {

    private static final String ROLE_ADMIN = "ADMIN";

    private final AdminUserMapper adminUserMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminRoleInterceptor(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String userId = request.getHeader("X-User-Id");
        if (!StringUtils.hasText(userId)) {
            writeError(response, Result.unauthorized("缺少用户身份（X-User-Id），请先登录"));
            return false;
        }
        Long uid;
        try {
            uid = Long.valueOf(userId);
        } catch (NumberFormatException e) {
            writeError(response, Result.unauthorized("非法的用户身份标识"));
            return false;
        }
        AdminUser user = adminUserMapper.selectById(uid);
        if (user == null) {
            writeError(response, Result.forbidden("用户不存在或无权访问管理平台"));
            return false;
        }
        if (!ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
            writeError(response, Result.forbidden("无管理员权限（role != ADMIN）"));
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, Result<?> result) throws IOException {
        response.setStatus(result.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
