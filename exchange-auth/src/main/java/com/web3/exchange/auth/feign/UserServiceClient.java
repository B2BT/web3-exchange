package com.web3.exchange.auth.feign;

import com.web3.exchange.auth.dto.response.UserInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务Feign客户端
 * name:注册到Nacos的服务名
 * configuration：Feign配置
 * fallback：降级处理类
 * path：统一前缀
 */
@FeignClient(
        name = "exchange-user",
        path = "/api/user"
)
public interface UserServiceClient {
    /**
     * //@PathVariable：将方法参数 username 的值填充到 @GetMapping 注解定义的 {username} 位置
     * @param username
     * @return
     */
    @GetMapping("/info/{username}")
    UserInfoResponse getUserInfo(@PathVariable("username") String username);

    @GetMapping("/{username}/authorities")
    List<String> getUserAuthorities(@PathVariable("username") String username);

    /**
     * //@RequestParam将方法参数 loginIp 的值以 ?key=value 的形式拼接在 URL 末尾。
     * @param username
     * @param loginIp
     */
    @PutMapping("/{username}/login-info")
    void updateLoginInfo(@PathVariable("username") String username,
                         @RequestParam("loginIp") String loginIp);

}
