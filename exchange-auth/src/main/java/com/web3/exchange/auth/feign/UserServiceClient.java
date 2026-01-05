package com.web3.exchange.auth.feign;

import com.web3.exchange.common.user.UserDetailDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

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
    UserDetailDTO getUserInfo(@PathVariable("username") String username);

    @GetMapping("/{username}/authorities")
    List<String> getUserAuthorities(@PathVariable("username") String username);

    @PutMapping("/{username}/login-info")
    void updateLoginInfo(@PathVariable("username") String username,
                         @RequestParam("loginIp") String loginIp);
}
