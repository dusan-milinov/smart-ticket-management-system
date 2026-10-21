package com.ticketmanagement.backend.dto.ticket;

import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.model.TicketEvent;

import java.time.Instant;

public record TicketEventResponse(
        Long id,
        TicketEventType eventType,
        String oldValue,
        String newValue,
        Instant createdAt,
        Long ticketId,
        Long userId
) {
    public static TicketEventResponse from(TicketEvent ticketEvent) {
        return new TicketEventResponse(
                ticketEvent.getId(),
                ticketEvent.getEventType(),
                ticketEvent.getOldValue(),
                ticketEvent.getNewValue(),
                ticketEvent.getCreatedAt(),
                ticketEvent.getTicket().getTicketId(),
                ticketEvent.getInitiator().getId()
        );
    }
}