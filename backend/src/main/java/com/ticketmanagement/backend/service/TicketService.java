package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.ticket.CreateTicketRequest;
import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.exception.InactiveAssigneeException;
import com.ticketmanagement.backend.exception.IllegalStatusException;
import com.ticketmanagement.backend.exception.TicketNotFoundException;
import com.ticketmanagement.backend.exception.UserNotFoundException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.TicketSpecifications;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketEventRecorder ticketEventRecorder;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketAssignmentService ticketAssignmentService;
    private final CurrentUserService currentUserService;
    private final TicketAccessPolicy ticketAccessPolicy;

    @Transactional
    @PreAuthorize("hasAnyRole('USER', 'SUPPORT_AGENT', 'ADMIN')")
    public TicketResponse createTicket(CreateTicketRequest request) {
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
        ticket.setAssignee(ticketAssignmentService.findLeastLoadedAgent());
        ticket.setTicketType(request.ticketType());

        Ticket savedTicket = ticketRepository.save(ticket);

        ticketEventRecorder.recordEvent(savedTicket.getTicketId(), creator.getId(), TicketEventType.CREATED, null, null);
        ticketEventRecorder.recordEvent(savedTicket.getTicketId(), creator.getId(), TicketEventType.ASSIGNED, null, savedTicket.getAssignee().getId().toString());

        return TicketResponse.from(savedTicket);
    }

    private String generateTicketNumber() {
        String ticketNumber;
        do {
            ticketNumber = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (ticketRepository.existsByTicketNumber(ticketNumber));
        return ticketNumber;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse reassign(long ticketID, long assigneeID) {
        AppUser actor = currentUserService.getCurrentUser();
        Ticket ticket = ticketRepository.findByTicketId(ticketID).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));
        // Locking the assignee serializes this check with a concurrent deactivation of the same account.
        AppUser assignee = userRepository.findByIdForUpdate(assigneeID).orElseThrow(() -> new UserNotFoundException("User with ID " + assigneeID + " not found."));
        if (!assignee.isActive()) {
            throw new InactiveAssigneeException(assigneeID);
        }
        String previousAssignee = ticket.getAssignee() == null ? null : ticket.getAssignee().getId().toString();
        ticket.assignTo(assignee);
        ticketEventRecorder.recordEvent(ticketID, actor.getId(), TicketEventType.REASSIGNED, previousAssignee, assigneeID + "");
        if (ticket.getTicketStatus() == TicketStatus.FULFILLMENT) {
            ticket.changeStatus(TicketStatus.IN_QUEUE);
            ticketEventRecorder.recordEvent(ticketID, actor.getId(), TicketEventType.STATUS_CHANGED,
                    TicketStatus.FULFILLMENT.name(), TicketStatus.IN_QUEUE.name());
        }
        return TicketResponse.from(ticket);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT_AGENT')")
    public TicketResponse resolveTicket(long ticketID, String summary){
        AppUser actor = currentUserService.getCurrentUser();
        Ticket ticket = ticketRepository.findByTicketId(ticketID).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));
        ticketAccessPolicy.requireRead(ticket, actor);

        ticketEventRecorder.recordEvent(ticketID, actor.getId(), TicketEventType.RESOLVED, ticket.getResolutionSummary(), summary);
        ticket.resolve(summary);
        return TicketResponse.from(ticket);
    }

    @Transactional
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public TicketResponse startTicketProcessing(long ticketID) {
        AppUser actor = currentUserService.getCurrentUser();
        Ticket ticket = ticketRepository.findByTicketId(ticketID)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));
        ticketAccessPolicy.requireAssignedAgent(ticket, actor);

        if (ticket.getTicketStatus() == TicketStatus.IN_QUEUE) {
            ticket.changeStatus(TicketStatus.FULFILLMENT);
            ticketEventRecorder.recordEvent(ticketID, actor.getId(), TicketEventType.STATUS_CHANGED,
                    TicketStatus.IN_QUEUE.name(), TicketStatus.FULFILLMENT.name());
        } else if (ticket.getTicketStatus() != TicketStatus.FULFILLMENT) {
            throw new IllegalStatusException("Only tickets in queue can be started.");
        }

        return TicketResponse.from(ticket);
    }


    @Transactional
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public TicketResponse cancelTicket(long ticketID){
        AppUser actor = currentUserService.getCurrentUser();
        Ticket ticket = ticketRepository.findByTicketId(ticketID).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));
        ticketAccessPolicy.requireRead(ticket, actor);

        ticketEventRecorder.recordEvent(ticketID, actor.getId(), TicketEventType.CLOSED, null, null);
        ticket.cancel();
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<TicketResponse> getAllTickets(Pageable pageable) {
        return ticketRepository.findAll(pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'SUPPORT_AGENT', 'ADMIN')")
    public TicketResponse getTicketById(Long ticketId) {
        Ticket t = ticketRepository.findByTicketId(ticketId).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        ticketAccessPolicy.requireRead(t, currentUserService.getCurrentUser());
        return TicketResponse.from(t);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<TicketResponse> getTicketsByStatus(TicketStatus status, Pageable pageable) {
        return ticketRepository.findByTicketStatus(status, pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public Page<TicketResponse> getTicketsByUserId(Long userId, Pageable pageable) {
        ticketAccessPolicy.requireCreatorScope(userId, currentUserService.getCurrentUser());
        return ticketRepository.findByCreatorId(userId, pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public Page<TicketResponse> searchTicketsByCreator(Long creatorId, TicketStatus status, TicketPriority priority,
                                                       String search, Pageable pageable) {
        ticketAccessPolicy.requireCreatorScope(creatorId, currentUserService.getCurrentUser());
        return ticketRepository.findAll(creatorFilter(creatorId, status, priority, search), pageable)
                .map(TicketResponse::from);
    }

    /** Per-status totals for the creator's tickets, honouring the priority and search filters. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public Map<TicketStatus, Long> countTicketsByCreator(Long creatorId, TicketPriority priority, String search) {
        ticketAccessPolicy.requireCreatorScope(creatorId, currentUserService.getCurrentUser());
        Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            counts.put(status, ticketRepository.count(creatorFilter(creatorId, status, priority, search)));
        }
        return counts;
    }

    private static Specification<Ticket> creatorFilter(Long creatorId, TicketStatus status, TicketPriority priority,
                                                       String search) {
        Specification<Ticket> scope = (root, query, cb) -> cb.equal(root.get("creator").get("id"), creatorId);
        return scope.and(TicketSpecifications.matching(status, priority, search));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT', 'ADMIN')")
    public Page<TicketResponse> searchTicketsByAssignee(Long assigneeId, TicketStatus status, TicketPriority priority,
                                                       String search, Pageable pageable) {
        ticketAccessPolicy.requireAssigneeScope(assigneeId, currentUserService.getCurrentUser());
        return ticketRepository.findAll(assigneeFilter(assigneeId, status, priority, search), pageable)
                .map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT', 'ADMIN')")
    public Map<TicketStatus, Long> countTicketsByAssignee(Long assigneeId, TicketPriority priority, String search) {
        ticketAccessPolicy.requireAssigneeScope(assigneeId, currentUserService.getCurrentUser());
        Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            counts.put(status, ticketRepository.count(assigneeFilter(assigneeId, status, priority, search)));
        }
        return counts;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT', 'ADMIN')")
    public List<TicketResponse> getAssignedPriorityQueue(Long assigneeId) {
        ticketAccessPolicy.requireAssigneeScope(assigneeId, currentUserService.getCurrentUser());
        return ticketRepository.findPriorityQueueByAssigneeIdAndTicketStatusIn(
                        assigneeId,
                        List.of(TicketStatus.IN_QUEUE, TicketStatus.FULFILLMENT),
                        PageRequest.of(0, 5))
                .stream()
                .map(TicketResponse::from)
                .toList();
    }

    private static Specification<Ticket> assigneeFilter(Long assigneeId, TicketStatus status, TicketPriority priority,
                                                        String search) {
        Specification<Ticket> scope = (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
        return scope.and(TicketSpecifications.matching(status, priority, search));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT', 'ADMIN')")
    public Page<TicketResponse> getTicketsByAssigneeId(Long assigneeId, Pageable pageable) {
        ticketAccessPolicy.requireAssigneeScope(assigneeId, currentUserService.getCurrentUser());
        return ticketRepository.findByAssigneeId(assigneeId, pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public Page<TicketResponse> getTicketsByUserIdAndStatus(Long userId, TicketStatus status, Pageable pageable) {
        ticketAccessPolicy.requireCreatorScope(userId, currentUserService.getCurrentUser());
        return ticketRepository.findByCreatorIdAndTicketStatus(userId, status, pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT', 'ADMIN')")
    public Page<TicketResponse> getTicketsByAssigneeIdAndStatus(Long assigneeId, TicketStatus status, Pageable pageable) {
        ticketAccessPolicy.requireAssigneeScope(assigneeId, currentUserService.getCurrentUser());
        return ticketRepository.findByAssigneeIdAndTicketStatus(assigneeId, status, pageable).map(TicketResponse::from);
    }

}
