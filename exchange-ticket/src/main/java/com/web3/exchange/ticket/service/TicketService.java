package com.web3.exchange.ticket.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.ticket.dto.TicketCreateDTO;
import com.web3.exchange.ticket.dto.TicketReplyDTO;
import com.web3.exchange.ticket.dto.TicketStatusDTO;
import com.web3.exchange.ticket.vo.TicketDetailVO;
import com.web3.exchange.ticket.vo.TicketReplyVO;
import com.web3.exchange.ticket.vo.TicketVO;

/**
 * 客服工单服务。
 */
public interface TicketService {
    // 用户侧
    TicketVO create(Long userId, TicketCreateDTO dto);
    Page<TicketVO> pageMine(Long userId, int page, int size, Integer status);
    TicketDetailVO detail(Long userId, Long ticketId);
    TicketReplyVO replyUser(Long userId, TicketReplyDTO dto);
    void close(Long userId, Long ticketId);
    // 管理侧
    Page<TicketVO> pageAll(int page, int size, Integer status);
    TicketReplyVO replyStaff(Long adminId, TicketReplyDTO dto);
    void updateStatus(Long adminId, TicketStatusDTO dto);
}
