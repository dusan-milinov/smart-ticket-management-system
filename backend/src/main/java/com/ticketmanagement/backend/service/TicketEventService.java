package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.ticket.TicketEventResponse;
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
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor

public class TicketEventService {

    private final TicketEventRepository ticketEventRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public void recordEvent(Long ticketId, Long actorID, TicketEventType eventType, String oldValue, String newValue) {

        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));

        AppUser actor = userRepository.findById(actorID)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + actorID + " not found."));

        // Create and save the event
        TicketEvent event = new TicketEvent();
        event.setTicket(ticket);
        event.setInitiator(actor);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        event.setEventType(eventType);

        ticketEventRepository.save(event);
    }

    public List<TicketEventResponse> getAllTicketEvents(long ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        return toResponses(ticketEventRepository.findAllByTicket(ticket));
    }

    private List<TicketEventResponse> toResponses(List<TicketEvent> events) {
        return events.stream().map(TicketEventResponse::from).toList();
    }
}
