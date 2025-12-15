package com.web3.exchange.common.entity.base;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类
 * 所有实体类都需要继承此类
 * abstract为了防止直接实例化，还可以抽象方法，让子类强制实现；
 * Serializable：序列化：对象->字节流；反序列化：字节流->对象；用于Redis、Session存储、消息队列传输、远程方法调用、持久化到文件
 */
@Data
@Accessors(chain = true)
public abstract class BaseEntity implements Serializable {

    // 序列化必须显式声明，类似于产品版本号，如果修改类后，没有版本号保存了数据，前后不一致就会崩溃；声明后就会兼容处理
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     * ASSIGN_ID: 雪花算法（推荐）
     * AUTO：数据库自增
     * ASSIGN_UUID: UUID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 创建人
     * fill = FieldFill.INSERT:插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人
     * fill = FieldFill.UPDATE:插入时自动更新
     */
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识
     * 0=未删除，1=已删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 租户ID：数据的身份证，多个客户使用，每个客户只能看到自己的数据
     */
    private Long tenantId;

    /**
     * 扩展字段（JSON格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String extData;

    /**
     * 获取实体表名（用于动态表明等场景）
     */
    public String getTableName() {
        TableName annotation = this.getClass().getAnnotation(TableName.class);
        return annotation != null ? annotation.value() : null;
    }

    /**
     * 判断是否为新增记录（根据id判断）
     * @return
     */
    public boolean isNew(){
        return this.getId() == null;
    }
}
