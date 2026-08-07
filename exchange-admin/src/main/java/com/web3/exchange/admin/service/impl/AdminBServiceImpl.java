package com.web3.exchange.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.dto.AnnouncementRequest;
import com.web3.exchange.admin.dto.HealthReportRequest;
import com.web3.exchange.admin.dto.SymbolRequest;
import com.web3.exchange.admin.entity.AdminAudit;
import com.web3.exchange.admin.entity.AdminSymbol;
import com.web3.exchange.admin.entity.AdminUser;
import com.web3.exchange.admin.entity.Announcement;
import com.web3.exchange.admin.entity.ServiceHealth;
import com.web3.exchange.admin.mapper.AdminAuditMapper;
import com.web3.exchange.admin.mapper.AdminUserMapper;
import com.web3.exchange.admin.mapper.AnnouncementMapper;
import com.web3.exchange.admin.mapper.ServiceHealthMapper;
import com.web3.exchange.admin.mapper.SymbolMapper;
import com.web3.exchange.admin.service.AdminBService;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin B 服务实现。
 */
@Slf4j
@Service
public class AdminBServiceImpl implements AdminBService {

    private final AnnouncementMapper announcementMapper;
    private final AdminAuditMapper auditMapper;
    private final ServiceHealthMapper healthMapper;
    private final SymbolMapper symbolMapper;
    private final AdminUserMapper adminUserMapper;

    public AdminBServiceImpl(AnnouncementMapper announcementMapper,
                             AdminAuditMapper auditMapper,
                             ServiceHealthMapper healthMapper,
                             SymbolMapper symbolMapper,
                             AdminUserMapper adminUserMapper) {
        this.announcementMapper = announcementMapper;
        this.auditMapper = auditMapper;
        this.healthMapper = healthMapper;
        this.symbolMapper = symbolMapper;
        this.adminUserMapper = adminUserMapper;
    }

    // ==================== 公告 ====================

    @Override
    public Page<Announcement> pageAnnouncements(int page, int size, String keyword) {
        LambdaQueryWrapper<Announcement> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(Announcement::getTitle, keyword.trim());
        }
        qw.orderByDesc(Announcement::getId);
        return announcementMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), qw);
    }

    @Override
    public Announcement createAnnouncement(AnnouncementRequest req, Long adminId) {
        if (!StringUtils.hasText(req.getTitle())) {
            throw new BusinessException("公告标题不能为空");
        }
        Announcement a = new Announcement();
        a.setId(IdWorker.getId());
        a.setTitle(req.getTitle().trim());
        a.setContent(req.getContent());
        a.setType(req.getType() == null ? 0 : req.getType());
        a.setStatus(0); // 草稿
        a.setPublisherId(adminId);
        a.setViewCount(0);
        announcementMapper.insert(a);
        audit(adminId, "CREATE", "ANNOUNCEMENT", String.valueOf(a.getId()), "创建公告: " + a.getTitle(), "");
        return a;
    }

    @Override
    public Announcement updateAnnouncement(AnnouncementRequest req, Long adminId) {
        Announcement a = requireAnnouncement(req.getId());
        if (StringUtils.hasText(req.getTitle())) a.setTitle(req.getTitle().trim());
        if (req.getContent() != null) a.setContent(req.getContent());
        if (req.getType() != null) a.setType(req.getType());
        announcementMapper.updateById(a);
        audit(adminId, "UPDATE", "ANNOUNCEMENT", String.valueOf(a.getId()), "编辑公告: " + a.getTitle(), "");
        return a;
    }

    @Override
    public Announcement publishAnnouncement(Long id, boolean publish, Long adminId) {
        Announcement a = requireAnnouncement(id);
        a.setStatus(publish ? 1 : 2);
        if (publish) a.setPublishTime(LocalDateTime.now());
        announcementMapper.updateById(a);
        audit(adminId, publish ? "PUBLISH" : "OFFLINE", "ANNOUNCEMENT", String.valueOf(id),
                (publish ? "发布" : "下线") + "公告: " + a.getTitle(), "");
        return a;
    }

    @Override
    public void deleteAnnouncement(Long id, Long adminId) {
        requireAnnouncement(id);
        announcementMapper.deleteById(id);
        audit(adminId, "DELETE", "ANNOUNCEMENT", String.valueOf(id), "删除公告ID=" + id, "");
    }

    // ==================== 审计 ====================

    @Override
    public void audit(Long adminId, String action, String targetType, String targetId, String detail, String ip) {
        try {
            AdminAudit a = new AdminAudit();
            a.setId(IdWorker.getId());
            a.setAdminUserId(adminId);
            AdminUser u = adminId != null ? adminUserMapper.selectById(adminId) : null;
            a.setAdminUsername(u != null ? u.getUsername() : String.valueOf(adminId));
            a.setAction(action);
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            a.setDetail(detail);
            a.setIp(ip);
            auditMapper.insert(a);
        } catch (Exception e) {
            log.warn("[admin-b] 审计日志写入失败: {}", e.getMessage());
        }
    }

    @Override
    public Page<AdminAudit> pageAudits(int page, int size, Long adminUserId) {
        LambdaQueryWrapper<AdminAudit> qw = new LambdaQueryWrapper<>();
        if (adminUserId != null) {
            qw.eq(AdminAudit::getAdminUserId, adminUserId);
        }
        qw.orderByDesc(AdminAudit::getId);
        return auditMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), qw);
    }

    // ==================== 服务健康 ====================

    @Override
    public Page<ServiceHealth> pageHealth(int page, int size) {
        return healthMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<ServiceHealth>().orderByAsc(ServiceHealth::getServiceName));
    }

    @Override
    public void reportHealth(HealthReportRequest req) {
        ServiceHealth exist = healthMapper.selectOne(new LambdaQueryWrapper<ServiceHealth>()
                .eq(ServiceHealth::getServiceName, req.getServiceName())
                .eq(ServiceHealth::getInstanceIp, req.getInstanceIp())
                .eq(ServiceHealth::getPort, req.getPort())
                .last("limit 1"), false);
        if (exist == null) {
            ServiceHealth h = new ServiceHealth();
            h.setId(IdWorker.getId());
            h.setServiceName(req.getServiceName());
            h.setInstanceIp(req.getInstanceIp());
            h.setPort(req.getPort());
            h.setStatus(req.getStatus() == null ? 1 : req.getStatus());
            h.setMemoryUsed(req.getMemoryUsed() == null ? 0 : req.getMemoryUsed());
            h.setMemoryTotal(req.getMemoryTotal() == null ? 0 : req.getMemoryTotal());
            h.setLastHeartbeat(LocalDateTime.now());
            healthMapper.insert(h);
        } else {
            healthMapper.update(null, new LambdaUpdateWrapper<ServiceHealth>()
                    .eq(ServiceHealth::getId, exist.getId())
                    .set(ServiceHealth::getStatus, req.getStatus() == null ? 1 : req.getStatus())
                    .set(ServiceHealth::getMemoryUsed, req.getMemoryUsed() == null ? 0 : req.getMemoryUsed())
                    .set(ServiceHealth::getMemoryTotal, req.getMemoryTotal() == null ? 0 : req.getMemoryTotal())
                    .set(ServiceHealth::getLastHeartbeat, LocalDateTime.now()));
        }
    }

    // ==================== 交易对 ====================

    @Override
    public Page<AdminSymbol> pageSymbols(int page, int size, String keyword) {
        LambdaQueryWrapper<AdminSymbol> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(AdminSymbol::getSymbol, keyword.trim());
        }
        qw.orderByAsc(AdminSymbol::getSort).orderByAsc(AdminSymbol::getId);
        return symbolMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), qw);
    }

    @Override
    public AdminSymbol createSymbol(SymbolRequest req, Long adminId) {
        if (!StringUtils.hasText(req.getSymbol()) || !StringUtils.hasText(req.getBaseCoin()) || !StringUtils.hasText(req.getQuoteCoin())) {
            throw new BusinessException("交易对符号/基础币/计价币均不能为空");
        }
        Long exist = symbolMapper.selectCount(new LambdaQueryWrapper<AdminSymbol>()
                .eq(AdminSymbol::getSymbol, req.getSymbol()));
        if (exist != null && exist > 0) {
            throw new BusinessException("交易对已存在: " + req.getSymbol());
        }
        AdminSymbol s = new AdminSymbol();
        s.setId(IdWorker.getId());
        s.setSymbol(req.getSymbol().trim().toUpperCase());
        s.setBaseCoin(req.getBaseCoin().trim().toUpperCase());
        s.setQuoteCoin(req.getQuoteCoin().trim().toUpperCase());
        s.setPricePrecision(req.getPricePrecision() == null ? 8 : req.getPricePrecision());
        s.setAmountPrecision(req.getAmountPrecision() == null ? 8 : req.getAmountPrecision());
        s.setPriceTick(req.getPriceTick() == null ? 1 : req.getPriceTick());
        s.setMinAmount(req.getMinAmount() == null ? 0 : req.getMinAmount());
        s.setMaxAmount(req.getMaxAmount());
        s.setMinNotional(req.getMinNotional() == null ? 0 : req.getMinNotional());
        s.setTakerFeeRate(req.getTakerFeeRate() == null ? 0 : req.getTakerFeeRate());
        s.setMakerFeeRate(req.getMakerFeeRate() == null ? 0 : req.getMakerFeeRate());
        s.setSort(req.getSort() == null ? 0 : req.getSort());
        s.setStatus(1);
        symbolMapper.insert(s);
        audit(adminId, "CREATE", "SYMBOL", s.getSymbol(), "新增交易对: " + s.getSymbol(), "");
        return s;
    }

    @Override
    public AdminSymbol updateSymbol(SymbolRequest req, Long adminId) {
        AdminSymbol s = requireSymbol(req.getId());
        if (req.getPricePrecision() != null) s.setPricePrecision(req.getPricePrecision());
        if (req.getAmountPrecision() != null) s.setAmountPrecision(req.getAmountPrecision());
        if (req.getPriceTick() != null) s.setPriceTick(req.getPriceTick());
        if (req.getMinAmount() != null) s.setMinAmount(req.getMinAmount());
        if (req.getMaxAmount() != null) s.setMaxAmount(req.getMaxAmount());
        if (req.getMinNotional() != null) s.setMinNotional(req.getMinNotional());
        if (req.getTakerFeeRate() != null) s.setTakerFeeRate(req.getTakerFeeRate());
        if (req.getMakerFeeRate() != null) s.setMakerFeeRate(req.getMakerFeeRate());
        if (req.getSort() != null) s.setSort(req.getSort());
        symbolMapper.updateById(s);
        audit(adminId, "UPDATE", "SYMBOL", s.getSymbol(), "编辑交易对: " + s.getSymbol(), "");
        return s;
    }

    @Override
    public AdminSymbol toggleSymbol(Long id, boolean trading, Long adminId) {
        AdminSymbol s = requireSymbol(id);
        s.setStatus(trading ? 1 : 0);
        symbolMapper.updateById(s);
        audit(adminId, trading ? "LISTING" : "SUSPEND", "SYMBOL", s.getSymbol(),
                (trading ? "上牌" : "停牌") + "交易对: " + s.getSymbol(), "");
        return s;
    }

    // ==================== 内部 ====================

    private Announcement requireAnnouncement(Long id) {
        Announcement a = id == null ? null : announcementMapper.selectById(id);
        if (a == null) {
            throw new NotFoundException("公告不存在: " + id);
        }
        return a;
    }

    private AdminSymbol requireSymbol(Long id) {
        AdminSymbol s = id == null ? null : symbolMapper.selectById(id);
        if (s == null) {
            throw new NotFoundException("交易对不存在: " + id);
        }
        return s;
    }
}
