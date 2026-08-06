package com.web3.exchange.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户表（t_user）——管理平台只读/管理用轻量实体。
 * <p>字段对齐既有表结构，含 {@code role}（USER/ADMIN）供后台鉴权与用户列表展示。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class AdminUser extends BaseEntity {
    /** 用户名 */
    private String username;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 昵称 */
    private String nickname;
    /** 账户状态:0=禁用,1=正常,2=锁定,3=冻结 */
    private Integer status;
    /** 角色:USER普通/ADMIN管理员 */
    private String role;
}
