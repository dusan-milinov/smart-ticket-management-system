package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.ticket.CreateTicketRequest;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.exception.UserNotFoundException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public TicketResponse createTicket(
            CreateTicketRequest request
    ) {

       AppUser creator = currentUserService.getCurrentUser();

        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = new Ticket();
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setTicketStatus(TicketStatus.IN_QUEUE);
        ticket.setTicketPriority(request.ticketPriority());
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket.setCreator(creator);
        ticket.setAssignee(null);
        ticket.setTicketType(request.ticketType());

        return TicketResponse.from(ticketRepository.save(ticket));
    }

    private String generateTicketNumber() {
        String ticketNumber;
        do {
            ticketNumber = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (ticketRepository.existsByTicketNumber(ticketNumber));
        return ticketNumber;
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
