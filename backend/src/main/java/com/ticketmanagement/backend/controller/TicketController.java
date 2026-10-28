package com.ticketmanagement.backend.controller;


import com.ticketmanagement.backend.dto.ticket.CreateTicketRequest;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.service.CurrentUserService;
import com.ticketmanagement.backend.service.TicketService;
import jakarta.validation.Valid;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;
    private final CurrentUserService currentUserService;

    @GetMapping("/my")
    public ResponseEntity<Page<TicketResponse>> getMyTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 5, sort = "ticketId", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.searchTicketsByCreator(
                currentUserService.getCurrentUser().getId(), status, priority, search, pageable));
    }

    @GetMapping("/my/status-counts")
    public ResponseEntity<Map<TicketStatus, Long>> getMyTicketStatusCounts(
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(ticketService.countTicketsByCreator(
                currentUserService.getCurrentUser().getId(), priority, search));
    }


    @GetMapping("/assigned")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<Page<TicketResponse>> getAssignedTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 5, sort = "ticketId", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.searchTicketsByAssignee(
                currentUserService.getCurrentUser().getId(), status, priority, search, pageable));
    }

    @GetMapping("/assigned/status-counts")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<Map<TicketStatus, Long>> getAssignedTicketStatusCounts(
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(ticketService.countTicketsByAssignee(
                currentUserService.getCurrentUser().getId(), priority, search));
    }

    @GetMapping("/assigned/priority-queue")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<List<TicketResponse>> getAssignedPriorityQueue() {
        return ResponseEntity.ok(ticketService.getAssignedPriorityQueue(
                currentUserService.getCurrentUser().getId()));
    }

    @PostMapping("/add")
    public ResponseEntity<TicketResponse> addTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {
        return new ResponseEntity<>(ticketService.createTicket(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<TicketResponse>> getAllTickets(
            @ParameterObject @PageableDefault(size = 5, sort = "ticketId") Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.getAllTickets(pageable));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getTicketById(ticketId));

    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<TicketResponse>> getTicketsByStatus(
            @PathVariable TicketStatus status,
            @ParameterObject @PageableDefault(size = 5, sort = "ticketId") Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.getTicketsByStatus(status, pageable));
    }

    @GetMapping("/creator/{creatorId}")
    public ResponseEntity<Page<TicketResponse>> getTicketsByUserId(
            @PathVariable Long creatorId,
            @ParameterObject @PageableDefault(size = 5, sort = "ticketId") Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.getTicketsByUserId(creatorId, pageable));
    }

    @GetMapping("/assignee/{assigneeId}")
    public ResponseEntity<Page<TicketResponse>> getTicketsByAssigneeId(
            @PathVariable Long assigneeId,
            @ParameterObject @PageableDefault(size = 5, sort = "ticketId") Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.getTicketsByAssigneeId(assigneeId, pageable));
    }

    @PatchMapping("/reassign")
    public ResponseEntity<TicketResponse> reassignTicket(@PathParam("ticketID") Long ticketID, @PathParam("assigneeId") Long assigneeId) {
        return ResponseEntity.ok(ticketService.reassign(ticketID, assigneeId));
    }

    @PatchMapping("/resolve")
    public ResponseEntity<TicketResponse> resolveTicket(@PathParam("ticketID") Long ticketID, @PathParam("summary") String summary) {
        return ResponseEntity.ok(ticketService.resolveTicket(ticketID, summary));
    }

    @PatchMapping("/{ticketId}/start")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public ResponseEntity<TicketResponse> startTicketProcessing(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.startTicketProcessing(ticketId));
    }

    @PatchMapping("/cancel")
    public ResponseEntity<TicketResponse> cancelTicket(@PathParam("ticketID") Long ticketID) {
        return ResponseEntity.ok(ticketService.cancelTicket(ticketID));
    }

}
