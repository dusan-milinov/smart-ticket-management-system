package com.ticketmanagement.backend.controller;


import com.ticketmanagement.backend.dto.ticket.CreateTicketRequest;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;

    @PostMapping("/add")
    public ResponseEntity<TicketResponse> addTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {
        return new ResponseEntity<>(ticketService.createTicket(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getTicketById(ticketId));

    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<String> deleteTicket(@PathVariable Long ticketId) {
        ticketService.deleteTicket(ticketId);
        return ResponseEntity.ok("Ticket deleted successfully.");
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<TicketResponse>> getTicketsByStatus(@PathVariable TicketStatus status) {
        return ResponseEntity.ok(ticketService.getTicketsByStatus(status));
    }

    @GetMapping("/creator/{creatorId}")
    public ResponseEntity<List<TicketResponse>> getTicketsByUserId(@PathVariable Long creatorId) {
        return ResponseEntity.ok(ticketService.getTicketsByUserId(creatorId));
    }

    @GetMapping("/assignee/{assigneeId}")
    public ResponseEntity<List<TicketResponse>> getTicketsByAssigneeId(@PathVariable Long assigneeId) {
        return ResponseEntity.ok(ticketService.getTicketsByAssigneeId(assigneeId));
    }
}
