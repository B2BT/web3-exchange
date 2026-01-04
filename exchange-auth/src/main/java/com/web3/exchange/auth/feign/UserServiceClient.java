package com.web3.exchange.auth.feign;

import com.web3.exchange.common.user.UserDetailDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@FeignClient(name = "exchange-user", path = "/api/user")
public interface UserServiceClient {

    @GetMapping("/info/{username}")
    UserDetailDTO getUserInfo(@PathVariable("username") String username);

    @GetMapping("/{username}/authorities")
    List<String> getUserAuthorities(@PathVariable("username") String username);

    @PutMapping("/{username}/login-info")
    void updateLoginInfo(@PathVariable("username") String username,
                         @RequestParam("loginIp") String loginIp);
}
