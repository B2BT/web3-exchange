package com.web3.exchange.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.exception.NotFoundException;
import com.web3.exchange.ticket.dto.TicketCreateDTO;
import com.web3.exchange.ticket.dto.TicketReplyDTO;
import com.web3.exchange.ticket.dto.TicketStatusDTO;
import com.web3.exchange.ticket.entity.Ticket;
import com.web3.exchange.ticket.entity.TicketReply;
import com.web3.exchange.ticket.mapper.TicketMapper;
import com.web3.exchange.ticket.mapper.TicketReplyMapper;
import com.web3.exchange.ticket.service.TicketService;
import com.web3.exchange.ticket.vo.TicketDetailVO;
import com.web3.exchange.ticket.vo.TicketReplyVO;
import com.web3.exchange.ticket.vo.TicketVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服工单服务实现。
 */
@Service
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;
    private final TicketReplyMapper replyMapper;

    public TicketServiceImpl(TicketMapper ticketMapper, TicketReplyMapper replyMapper) {
        this.ticketMapper = ticketMapper;
        this.replyMapper = replyMapper;
    }

    @Override
    public TicketVO create(Long userId, TicketCreateDTO dto) {
        Ticket t = new Ticket();
        t.setId(IdWorker.getId());
        t.setUserId(userId);
        t.setCategory(dto.getCategory());
        t.setTitle(dto.getTitle().trim());
        t.setContent(dto.getContent());
        t.setStatus(0);
        t.setPriority(dto.getPriority() == null ? 1 : dto.getPriority());
        ticketMapper.insert(t);
        return toVO(t);
    }

    @Override
    public Page<TicketVO> pageMine(Long userId, int page, int size, Integer status) {
        LambdaQueryWrapper<Ticket> qw = new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getUserId, userId);
        if (status != null) qw.eq(Ticket::getStatus, status);
        qw.orderByDesc(Ticket::getId);
        Page<Ticket> p = ticketMapper.selectPage(new Page<>(clamp(page), clamp(size)), qw);
        Page<TicketVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return vo;
    }

    @Override
    public TicketDetailVO detail(Long userId, Long ticketId) {
        Ticket t = requireOwned(userId, ticketId);
        TicketDetailVO vo = new TicketDetailVO();
        vo.setId(t.getId());
        vo.setUserId(t.getUserId());
        vo.setCategory(t.getCategory());
        vo.setTitle(t.getTitle());
        vo.setContent(t.getContent());
        vo.setStatus(t.getStatus());
        vo.setPriority(t.getPriority());
        vo.setAssigneeId(t.getAssigneeId());
        vo.setResolvedAt(t.getResolvedAt());
        vo.setCreateTime(t.getCreateTime());
        vo.setReplies(listReplies(ticketId));
        return vo;
    }

    @Override
    @Transactional
    public TicketReplyVO replyUser(Long userId, TicketReplyDTO dto) {
        requireOwned(userId, dto.getTicketId());
        return insertReply(dto.getTicketId(), userId, 0, dto.getContent());
    }

    @Override
    public void close(Long userId, Long ticketId) {
        Ticket t = requireOwned(userId, ticketId);
        if (t.getStatus() == 3) return;
        t.setStatus(3);
        ticketMapper.updateById(t);
    }

    @Override
    public Page<TicketVO> pageAll(int page, int size, Integer status) {
        LambdaQueryWrapper<Ticket> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(Ticket::getStatus, status);
        qw.orderByDesc(Ticket::getId);
        Page<Ticket> p = ticketMapper.selectPage(new Page<>(clamp(page), clamp(size)), qw);
        Page<TicketVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return vo;
    }

    @Override
    @Transactional
    public TicketReplyVO replyStaff(Long adminId, TicketReplyDTO dto) {
        requireTicket(dto.getTicketId());
        // 管理员回复后，工单回到待处理/处理中
        Ticket t = requireTicket(dto.getTicketId());
        if (t.getStatus() == 3) throw new BusinessException("工单已关闭，无法回复");
        if (t.getStatus() == 0) {
            t.setStatus(1);
            t.setAssigneeId(adminId);
            ticketMapper.updateById(t);
        }
        return insertReply(dto.getTicketId(), adminId, 1, dto.getContent());
    }

    @Override
    @Transactional
    public void updateStatus(Long adminId, TicketStatusDTO dto) {
        Ticket t = requireTicket(dto.getTicketId());
        t.setStatus(dto.getStatus());
        if (dto.getAssigneeId() != null) t.setAssigneeId(dto.getAssigneeId());
        if (dto.getStatus() == 2) t.setResolvedAt(LocalDateTime.now());
        ticketMapper.updateById(t);
    }

    private TicketReplyVO insertReply(Long ticketId, Long userId, int isStaff, String content) {
        TicketReply r = new TicketReply();
        r.setId(IdWorker.getId());
        r.setTicketId(ticketId);
        r.setUserId(userId);
        r.setIsStaff(isStaff);
        r.setContent(content);
        replyMapper.insert(r);
        TicketReplyVO vo = new TicketReplyVO();
        vo.setId(r.getId());
        vo.setTicketId(ticketId);
        vo.setUserId(userId);
        vo.setIsStaff(isStaff);
        vo.setContent(content);
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    private List<TicketReplyVO> listReplies(Long ticketId) {
        return replyMapper.selectList(new LambdaQueryWrapper<TicketReply>()
                        .eq(TicketReply::getTicketId, ticketId)
                        .orderByAsc(TicketReply::getId))
                .stream().map(this::toReplyVO).toList();
    }

    private Ticket requireOwned(Long userId, Long ticketId) {
        Ticket t = requireTicket(ticketId);
        if (!t.getUserId().equals(userId)) {
            throw new NotFoundException("工单不存在: " + ticketId);
        }
        return t;
    }

    private Ticket requireTicket(Long ticketId) {
        Ticket t = ticketMapper.selectById(ticketId);
        if (t == null) throw new NotFoundException("工单不存在: " + ticketId);
        return t;
    }

    private TicketVO toVO(Ticket t) {
        TicketVO vo = new TicketVO();
        vo.setId(t.getId());
        vo.setUserId(t.getUserId());
        vo.setCategory(t.getCategory());
        vo.setTitle(t.getTitle());
        vo.setContent(t.getContent());
        vo.setStatus(t.getStatus());
        vo.setPriority(t.getPriority());
        vo.setAssigneeId(t.getAssigneeId());
        vo.setResolvedAt(t.getResolvedAt());
        vo.setCreateTime(t.getCreateTime());
        vo.setUpdateTime(t.getUpdateTime());
        return vo;
    }

    private TicketReplyVO toReplyVO(TicketReply r) {
        TicketReplyVO vo = new TicketReplyVO();
        vo.setId(r.getId());
        vo.setTicketId(r.getTicketId());
        vo.setUserId(r.getUserId());
        vo.setIsStaff(r.getIsStaff());
        vo.setContent(r.getContent());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    private int clamp(int v) {
        return Math.min(Math.max(v, 1), 100);
    }
}
