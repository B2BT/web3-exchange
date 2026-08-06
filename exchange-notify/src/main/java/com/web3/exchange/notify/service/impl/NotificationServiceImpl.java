package com.web3.exchange.notify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.notify.entity.Notification;
import com.web3.exchange.notify.mapper.NotificationMapper;
import com.web3.exchange.notify.service.NotificationService;
import com.web3.exchange.notify.vo.NotificationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 站内通知服务实现。
 * <p>
 * 写入路径：消费端调用 {@link #createWithIdempotent}，依靠
 * {@code uk_user_type_bizref(user_id, type, biz_ref)} 唯一索引作为最终幂等防线——
 * 同一用户同一类型同一业务单号只落一条通知，重复事件 insert 撞唯一索引后捕获 DuplicateKeyException 跳过。
 * </p>
 */
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Override
    public boolean createWithIdempotent(Notification notification) {
        if (notification.getUserId() == null || notification.getType() == null || notification.getBizRef() == null) {
            log.warn("通知字段不完整，跳过写入。userId={}, type={}, bizRef={}",
                    notification.getUserId(), notification.getType(), notification.getBizRef());
            return false;
        }
        try {
            this.save(notification);
            return true;
        } catch (DuplicateKeyException e) {
            // 唯一索引命中 → 该通知已处理过，跳过（幂等）
            log.info("重复通知事件，幂等跳过。userId={}, type={}, bizRef={}",
                    notification.getUserId(), notification.getType(), notification.getBizRef());
            return false;
        }
    }

    @Override
    public Page<NotificationVO> pageByUser(Long userId, Integer isRead, int page, int size) {
        Page<Notification> p = this.page(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(isRead != null, Notification::getIsRead, isRead)
                        .orderByDesc(Notification::getCreateTime));
        Page<NotificationVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public long unreadCount(Long userId) {
        return baseMapper.countUnread(userId);
    }

    @Override
    public boolean markRead(Long id, Long userId) {
        // 校验归属：仅当通知存在且属于该用户且未删时更新
        LambdaUpdateWrapper<Notification> uw = new LambdaUpdateWrapper<>();
        uw.eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .set(Notification::getReadTime, java.time.LocalDateTime.now());
        return this.update(uw);
    }

    @Override
    public int markAllRead(Long userId) {
        return baseMapper.updateAllRead(userId);
    }

    @Override
    public NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setUserId(n.getUserId());
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setBizRef(n.getBizRef());
        vo.setSymbol(n.getSymbol());
        vo.setAmount(n.getAmount());
        vo.setIsRead(n.getIsRead());
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}
