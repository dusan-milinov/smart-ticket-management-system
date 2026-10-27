package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.dashboard.AdminDashboardResponse;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final List<TicketStatus> OPEN_STATUSES = List.of(TicketStatus.IN_QUEUE, TicketStatus.FULFILLMENT);
    private final TicketRepository tickets;
    private final UserRepository users;

    /** Returns system-wide counts and the five newest tickets without loading the full ticket/account directories. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardResponse getSummary() {
        var statuses = new EnumMap<TicketStatus, Long>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            statuses.put(status, tickets.countByTicketStatus(status));
        }
        var priorities = new EnumMap<TicketPriority, Long>(TicketPriority.class);
        for (TicketPriority priority : TicketPriority.values()) {
            priorities.put(priority, tickets.countByTicketPriorityAndTicketStatusIn(priority, OPEN_STATUSES));
        }
        var recent = tickets.findAll(PageRequest.of(0, 5,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("ticketId"))))
                .map(TicketResponse::from).getContent();

        return new AdminDashboardResponse(
                statuses.values().stream().mapToLong(Long::longValue).sum(), statuses, priorities,
                tickets.countByAssigneeIsNullAndTicketStatusIn(OPEN_STATUSES),
                users.count(), users.countByRole(Role.USER), users.countByRole(Role.SUPPORT_AGENT),
                users.countByRoleAndActiveTrue(Role.SUPPORT_AGENT), recent);
    }
}
