package com.web3.exchange.auth.feign;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.common.user.UserDetailDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Map;

/**
 * 用户服务Feign客户端
 */
@FeignClient(
        name = "exchange-user",           // 服务名（在nacos中注册的名字）
        contextId = "userServiceClient", // 上下文ID，避免重复
        path = "/api/users"             // 路径前缀
)
public interface UserServiceClient {

    @GetMapping("/info/{username}")
    Result<UserDetailDTO> getUserInfo(@PathVariable("username") String username);

    @GetMapping("/{username}/authorities")
    List<String> getUserAuthorities(@PathVariable("username") String username);

    @PutMapping("/{username}/login-info")
    void updateLoginInfo(@PathVariable("username") String username,
                         @RequestParam("loginIp") String loginIp);

    /**
     * 注册用户（对应 user 服务的 POST /api/users/register，入参与 RegisterDTO 字段一致）
     */
    @PostMapping("/register")
    Result<Void> register(@RequestBody Map<String, Object> dto);

    /**
     * 更新用户密码（对应 user 服务的 PUT /api/users/{id}/password）
     */
    @PutMapping("/{id}/password")
    Result<Void> updatePassword(@PathVariable("id") Long id,
                                @RequestBody Map<String, String> body);
}
