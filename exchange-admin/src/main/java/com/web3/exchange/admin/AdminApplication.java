package com.web3.exchange.admin;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 管理平台模块启动类（exchange-admin，端口 8109）。
 * <p>
 * 运营后台：直接连同一 mysql 查询/操作 t_user/t_order/t_withdraw/t_wallet_account，
 * 提供 /api/admin/** 全站管理接口（需 ADMIN 角色，网关注入 X-User-Id 后由本域
 * {@code AdminRoleInterceptor} 校验 role）。提现审核通过 Feign 调 exchange-chain 的
 * 内部审核接口，复用其「冻结上链 / 拒绝 / 回滚」资产逻辑。
 * 通过 @Import(GlobalExceptionHandler.class) 引入公共异常处理，@MapperScan 扫描本模块 mapper。
 * </p>
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@MapperScan("com.web3.exchange.admin.mapper")
@EnableFeignClients(basePackages = "com.web3.exchange.admin.feign")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
