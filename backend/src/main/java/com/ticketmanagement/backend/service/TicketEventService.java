package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.ticket.TicketEventResponse;
import com.ticketmanagement.backend.dto.ticket.ResolvedTicketDay;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.exception.UserNotFoundException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.model.TicketEvent;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor

public class TicketEventService {

    private final TicketEventRepository ticketEventRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordEvent(Long ticketId, Long actorID, TicketEventType eventType, String oldValue, String newValue) {

        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));

        AppUser actor = userRepository.findById(actorID)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + actorID + " not found."));

        TicketEvent event = new TicketEvent();
        event.setTicket(ticket);
        event.setInitiator(actor);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        event.setEventType(eventType);

        ticket.setUpdatedAt(java.time.Instant.now()); // Svaki event bi trebao da ažurira vreme poslednje izmene tiketa
        ticketRepository.save(ticket);

        ticketEventRepository.save(event);
    }

    public List<TicketEventResponse> getAllTicketEvents(long ticketId) {
        Ticket ticket = ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        return toResponses(ticketEventRepository.findAllByTicket(ticket));
    }

    /** Daily UTC counts attributed to the agent who performed each resolution, including today. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ResolvedTicketDay> getDailyResolutions(Long agentId, int days) {
        if (days != 7 && days != 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Days must be 7 or 30.");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(days - 1L);
        Map<LocalDate, Long> counts = ticketEventRepository.countDailyResolutions(
                        agentId,
                        start.atStartOfDay().toInstant(ZoneOffset.UTC),
                        today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
                .stream()
                .collect(Collectors.toMap(ResolvedTicketDay::date, ResolvedTicketDay::count));
        return IntStream.range(0, days)
                .mapToObj(index -> {
                    LocalDate date = start.plusDays(index);
                    return new ResolvedTicketDay(date, counts.getOrDefault(date, 0L));
                })
                .toList();
    }

    private List<TicketEventResponse> toResponses(List<TicketEvent> events) {
        return events.stream().map(TicketEventResponse::from).toList();
    }
}
