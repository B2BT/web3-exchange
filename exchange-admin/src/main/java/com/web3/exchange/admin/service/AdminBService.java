package com.web3.exchange.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.dto.AnnouncementRequest;
import com.web3.exchange.admin.dto.HealthReportRequest;
import com.web3.exchange.admin.dto.SymbolRequest;
import com.web3.exchange.admin.entity.AdminAudit;
import com.web3.exchange.admin.entity.AdminSymbol;
import com.web3.exchange.admin.entity.Announcement;
import com.web3.exchange.admin.entity.ServiceHealth;

/**
 * Admin B 服务：公告管理、审计日志、服务健康、交易对管理。
 */
public interface AdminBService {

    // ---- 公告 ----
    Page<Announcement> pageAnnouncements(int page, int size, String keyword);
    Announcement createAnnouncement(AnnouncementRequest req, Long adminId);
    Announcement updateAnnouncement(AnnouncementRequest req, Long adminId);
    Announcement publishAnnouncement(Long id, boolean publish, Long adminId);
    void deleteAnnouncement(Long id, Long adminId);

    // ---- 审计 ----
    void audit(Long adminId, String action, String targetType, String targetId, String detail, String ip);
    Page<AdminAudit> pageAudits(int page, int size, Long adminUserId);

    // ---- 服务健康 ----
    Page<ServiceHealth> pageHealth(int page, int size);
    void reportHealth(HealthReportRequest req);

    // ---- 交易对 ----
    Page<AdminSymbol> pageSymbols(int page, int size, String keyword);
    AdminSymbol createSymbol(SymbolRequest req, Long adminId);
    AdminSymbol updateSymbol(SymbolRequest req, Long adminId);
    AdminSymbol toggleSymbol(Long id, boolean trading, Long adminId);
}
