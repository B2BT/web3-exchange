package com.web3.exchange.notify.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.notify.entity.Notification;
import com.web3.exchange.notify.vo.NotificationVO;

/**
 * 站内通知服务。
 * <p>
 * 提供幂等写入（消费端调用）、分页查询、未读数、单条已读、全部已读。
 * 幂等以 t_notification.uk_user_type_bizref(user_id, type, biz_ref) 唯一索引兜底：
 * createWithIdempotent 捕获 DuplicateKeyException 后跳过，重复事件不生成重复通知。
 * </p>
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 幂等写入一条通知：INSERT 撞唯一索引（uk_user_type_bizref）则跳过（已处理过）。
     *
     * @param notification 通知实体（须含 userId/type/bizRef）
     * @return true=本次新写入；false=重复事件已存在，跳过
     */
    boolean createWithIdempotent(Notification notification);

    /**
     * 分页查询用户通知（按 create_time 倒序），支持按已读状态过滤。
     */
    Page<NotificationVO> pageByUser(Long userId, Integer isRead, int page, int size);

    /**
     * 统计用户未读通知数。
     */
    long unreadCount(Long userId);

    /**
     * 标记单条已读（校验归属 user_id==userId，越权返回 false）。
     *
     * @return true=已读成功；false=通知不存在或不属于该用户
     */
    boolean markRead(Long id, Long userId);

    /**
     * 将用户全部未读通知置为已读。
     *
     * @return 更新条数
     */
    int markAllRead(Long userId);

    /**
     * 实体转 VO。
     */
    NotificationVO toVO(Notification n);
}
