package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.ticket.TicketEventResponse;
import com.ticketmanagement.backend.service.TicketEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ticket-events")
@RequiredArgsConstructor

public class TicketEventController {
    private final TicketEventService ticketEventService;

    @GetMapping("/{ticketId}")
    public ResponseEntity<List<TicketEventResponse>> getAllTicketEvents(@PathVariable long ticketId) {
        return ResponseEntity.ok(ticketEventService.getAllTicketEvents(ticketId));
    }
}
