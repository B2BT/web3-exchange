package com.web3.exchange.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.time.LocalDateTime;


/**
 * MyBatis-Plus公共配置
 * 所有微服务共享此配置
 */
@Configuration
@EnableTransactionManagement
@MapperScan({
        "com.web3.exchange.user.mapper",      // 用户服务
        //"com.example.order.mapper",     // 订单服务
        //"com.example.product.mapper"    // 产品服务
        // 可以继续添加其他微服务的mapper包
})
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus插件配置
     * 所有微服务共享
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 分页插件
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(1000L);  // 最大分页数
        pagination.setOverflow(true);   // 超过最大页数时回到第一页
        interceptor.addInnerInterceptor(pagination);

        // 2. 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 3. 防止全表更新与删除
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }

    /**
     * 自动填充处理器
     * 处理create_time, update_time等公共字段
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "createBy", String.class, getCurrentUser());
                this.strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                this.strictUpdateFill(metaObject, "updateBy", String.class, getCurrentUser());
            }

            private String getCurrentUser() {
                // 可以从SecurityContext或ThreadLocal获取
                // 这里简化处理
                return "system";
            }
        };
    }

    /**
     * 公共实体基类（可选）
     */
    public static class BaseEntity {
        // 公共字段定义
    }
}
