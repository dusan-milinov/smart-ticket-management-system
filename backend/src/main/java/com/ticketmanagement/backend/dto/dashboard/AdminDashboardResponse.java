package com.ticketmanagement.backend.dto.dashboard;

import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;

import java.util.List;
import java.util.Map;

public record AdminDashboardResponse(
        long totalTickets,
        Map<TicketStatus, Long> ticketsByStatus,
        Map<TicketPriority, Long> openTicketsByPriority,
        long unassignedOpenTickets,
        long totalAccounts,
        long regularUsers,
        long supportAgents,
        long activeSupportAgents,
        List<TicketResponse> recentTickets
) {
}
