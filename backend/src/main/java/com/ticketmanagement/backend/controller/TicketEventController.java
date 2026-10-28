package com.ticketmanagement.backend.controller;

import com.ticketmanagement.backend.dto.ticket.TicketEventResponse;
import com.ticketmanagement.backend.dto.ticket.ResolvedTicketDay;
import com.ticketmanagement.backend.service.CurrentUserService;
import com.ticketmanagement.backend.service.TicketEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/ticket-events")
@RequiredArgsConstructor

public class TicketEventController {
    private final TicketEventService ticketEventService;
    private final CurrentUserService currentUserService;

    @GetMapping("/my/resolved-daily")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<List<ResolvedTicketDay>> getDailyResolutions(
            @RequestParam(defaultValue = "7") int days
    ) {
        return ResponseEntity.ok(ticketEventService.getDailyResolutions(
                currentUserService.getCurrentUser().getId(), days));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<List<TicketEventResponse>> getAllTicketEvents(@PathVariable long ticketId) {
        return ResponseEntity.ok(ticketEventService.getAllTicketEvents(ticketId));
    }
}
