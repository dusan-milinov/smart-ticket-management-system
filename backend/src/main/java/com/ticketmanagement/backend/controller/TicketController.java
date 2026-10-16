package com.ticketmanagement.backend.controller;


import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;

    @PostMapping("/add")
    public ResponseEntity<Ticket> addTicket(@RequestBody Ticket ticket) {
        return new ResponseEntity<>(ticketService.createTicket(ticket), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Ticket>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<Ticket> getTicketById(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getTicketById(ticketId));

    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<String> deleteTicket(@PathVariable Long ticketId) {
        ticketService.deleteTicket(ticketId);
        return ResponseEntity.ok("Ticket deleted successfully.");
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Ticket>> getTicketsByStatus(@PathVariable TicketStatus status) {
        return ResponseEntity.ok(ticketService.getTicketsByStatus(status));
    }

    @GetMapping("/creator/{creatorId}")
    public ResponseEntity<List<Ticket>> getTicketsByUserId(@PathVariable Long creatorId) {
        return ResponseEntity.ok(ticketService.getTicketsByUserId(creatorId));
    }

    @GetMapping("/assignee/{assigneeId}")
    public ResponseEntity<List<Ticket>> getTicketsByAssigneeId(@PathVariable Long assigneeId) {
        return ResponseEntity.ok(ticketService.getTicketsByAssigneeId(assigneeId));
    }
}
