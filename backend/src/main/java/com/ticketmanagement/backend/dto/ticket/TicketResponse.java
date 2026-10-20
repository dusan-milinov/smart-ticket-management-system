package com.ticketmanagement.backend.dto.ticket;

import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.enums.TicketType;
import com.ticketmanagement.backend.model.Ticket;

import java.time.Instant;
import java.time.LocalDateTime;

public record TicketResponse(
        Long ticketId,
        String ticketNumber,
        String title,
        String description,
        TicketStatus ticketStatus,
        TicketPriority ticketPriority,
        String resolutionSummary,
        Instant createdAt,
        Instant updatedAt,
        Long creatorId,
        Long assigneeId,
        TicketType ticketType
) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getTicketId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getTicketStatus(),
                ticket.getTicketPriority(),
                ticket.getResolutionSummary(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getCreator().getId(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                ticket.getTicketType()
        );
    }
}
