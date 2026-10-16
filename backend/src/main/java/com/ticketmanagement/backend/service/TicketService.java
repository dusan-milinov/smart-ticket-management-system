package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.exception.TicketAlreadyExistsException;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;

    public Ticket createTicket(Ticket ticket) {
        if (ticketRepository.existsByTicketId(ticket.getTicketId())) {
            throw new TicketAlreadyExistsException("Ticket with ID " + ticket.getTicketId() + " already exists.");
        }
        return ticketRepository.save(ticket);
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public Ticket getTicketById(Long ticketId) {
        Ticket t = ticketRepository.findByTicketId(ticketId).orElse(null);
        if (t == null) {
            throw new TicketNotFoundException("Ticket with ID " + ticketId + " not found.");
        }
        return t;
    }

    public void deleteTicket(Long ticketId) {
        if (!ticketRepository.existsByTicketId(ticketId)) {
            throw new TicketNotFoundException("Ticket with ID " + ticketId + " not found.");
        }
        ticketRepository.deleteByTicketId(ticketId);
    }

    public List<Ticket> getTicketsByStatus(TicketStatus status) {
        return ticketRepository.findByTicketStatus(status);
    }

    public List<Ticket> getTicketsByUserId(Long userId) {
        return ticketRepository.findByCreatorId(userId);
    }

    public List<Ticket> getTicketsByAssigneeId(Long assigneeId) {
        return ticketRepository.findByAssigneeId(assigneeId);
    }
}
