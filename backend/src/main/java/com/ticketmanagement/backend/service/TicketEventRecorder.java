package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.exception.UserNotFoundException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.model.TicketEvent;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Trusted internal writer: callers authorize the operation and supply the authenticated actor. */
@Service
@RequiredArgsConstructor
class TicketEventRecorder {

    private final TicketEventRepository ticketEventRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    // Events must join the ticket/comment transaction, never commit independently.
    @Transactional(propagation = Propagation.MANDATORY)
    void recordEvent(Long ticketId, Long actorId, TicketEventType eventType, String oldValue, String newValue) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        AppUser actor = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + actorId + " not found."));

        TicketEvent event = new TicketEvent();
        event.setTicket(ticket);
        event.setInitiator(actor);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        event.setEventType(eventType);
        ticket.setUpdatedAt(Instant.now());
        ticketRepository.save(ticket);
        ticketEventRepository.save(event);
    }
}
