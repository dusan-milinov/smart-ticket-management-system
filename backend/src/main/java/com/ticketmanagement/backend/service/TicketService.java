package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.ticket.CreateTicketRequest;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.exception.UserNotFoundException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketEventService ticketEventService;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public TicketResponse createTicket(
            CreateTicketRequest request
    ) {

       AppUser creator = currentUserService.getCurrentUser();

        Instant now = Instant.now();
        Ticket ticket = new Ticket();
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setTicketStatus(TicketStatus.IN_QUEUE);
        ticket.setTicketPriority(request.ticketPriority());
        ticket.setResolutionSummary(null);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket.setCreator(creator);
        ticket.setAssignee(null);
        ticket.setTicketType(request.ticketType());

        Ticket savedTicket = ticketRepository.save(ticket);

        //TODO: Add actor to event recording, currently 1
        ticketEventService.recordEvent(savedTicket.getTicketId(), (long)1, TicketEventType.CREATED, null, null);
        return TicketResponse.from(savedTicket);
    }

    private String generateTicketNumber() {
        String ticketNumber;
        do {
            ticketNumber = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (ticketRepository.existsByTicketNumber(ticketNumber));
        return ticketNumber;
    }

    public void reassign(long ticketID, long assigneeID, long actorID) {
        Ticket ticket = ticketRepository.findByTicketId(ticketID).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));
        AppUser assignee = userRepository.findById(assigneeID).orElseThrow(() -> new UserNotFoundException("User with ID " + assigneeID + " not found."));
        ticketEventService.recordEvent(ticketID, actorID, TicketEventType.REASSIGNED, ticket.getAssignee() == null ? null : ticket.getAssignee().getId().toString(), assigneeID + "");
        ticket.assignTo(assignee);
    }

    public void resolveTicket(long ticketID, String summary, long actorID){
        Ticket ticket = ticketRepository.findByTicketId(ticketID).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));

        ticketEventService.recordEvent(ticketID, actorID, TicketEventType.RESOLVED, ticket.getResolutionSummary(), summary);
        ticket.resolve(summary);
    }

    public List<TicketResponse> getAllTickets() {
        return toResponses(ticketRepository.findAll());
    }

    public TicketResponse getTicketById(Long ticketId) {
        Ticket t = ticketRepository.findByTicketId(ticketId).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        return TicketResponse.from(t);
    }

    public void deleteTicket(Long ticketId) {
        Ticket t = ticketRepository.findByTicketId(ticketId).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        ticketRepository.delete(t);
    }

    public List<TicketResponse> getTicketsByStatus(TicketStatus status) {
        return toResponses(ticketRepository.findByTicketStatus(status));
    }

    public List<TicketResponse> getTicketsByUserId(Long userId) {
        return toResponses(ticketRepository.findByCreatorId(userId));
    }

    public List<TicketResponse> getTicketsByAssigneeId(Long assigneeId) {
        return toResponses(ticketRepository.findByAssigneeId(assigneeId));
    }

    private List<TicketResponse> toResponses(List<Ticket> tickets) {
        return tickets.stream().map(TicketResponse::from).toList();
    }
}
