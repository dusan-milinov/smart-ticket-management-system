package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.ticket.TicketEventResponse;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.model.TicketEvent;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor

public class TicketEventService {

    private final TicketEventRepository ticketEventRepository;
    private final TicketRepository ticketRepository;
    private final CurrentUserService currentUserService;
    private final TicketAccessPolicy ticketAccessPolicy;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'SUPPORT_AGENT', 'ADMIN')")
    public List<TicketEventResponse> getAllTicketEvents(long ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        ticketAccessPolicy.requireRead(ticket, currentUserService.getCurrentUser());
        return toResponses(ticketEventRepository.findAllByTicket(ticket));
    }

    private List<TicketEventResponse> toResponses(List<TicketEvent> events) {
        return events.stream().map(TicketEventResponse::from).toList();
    }
}
