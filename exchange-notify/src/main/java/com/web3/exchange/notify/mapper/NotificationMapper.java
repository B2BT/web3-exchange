package com.web3.exchange.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.notify.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 站内通知 Mapper（t_notification）。
 * <p>
 * 继承 {@link BaseMapper} 获得通用 CRUD；额外提供未读数统计与批量已读（走 idx_user_read_time 索引）。
 * </p>
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 统计用户未读通知数（count(is_read=0)）。
     */
    @Select("SELECT COUNT(*) FROM t_notification WHERE user_id=#{userId} AND is_read=0 AND is_deleted=0")
    long countUnread(@Param("userId") Long userId);

    /**
     * 将用户全部未读通知置为已读，返回更新条数。
     */
    @Update("UPDATE t_notification SET is_read=1, read_time=NOW(), update_time=NOW() " +
            "WHERE user_id=#{userId} AND is_read=0 AND is_deleted=0")
    int updateAllRead(@Param("userId") Long userId);
}
