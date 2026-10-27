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
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketEventService ticketEventService;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketAssignmentService ticketAssignmentService;

    @Transactional
    public TicketResponse createTicket(
            CreateTicketRequest request,
            AppUser creator
    ) {

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

        ticketEventService.recordEvent(savedTicket.getTicketId(), creator.getId(), TicketEventType.CREATED, null, null);
        ticketEventService.recordEvent(savedTicket.getTicketId(), creator.getId(), TicketEventType.ASSIGNED, null, savedTicket.getAssignee().getId().toString());

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
    public TicketResponse reassign(long ticketID, long assigneeID, long actorID) {
        Ticket ticket = ticketRepository.findByTicketId(ticketID).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));
        // Locking the assignee serializes this check with a concurrent deactivation of the same account.
        AppUser assignee = userRepository.findByIdForUpdate(assigneeID).orElseThrow(() -> new UserNotFoundException("User with ID " + assigneeID + " not found."));
        if (!assignee.isActive()) {
            throw new InactiveAssigneeException(assigneeID);
        }
        ticketEventService.recordEvent(ticketID, actorID, TicketEventType.REASSIGNED, ticket.getAssignee() == null ? null : ticket.getAssignee().getId().toString(), assigneeID + "");
        ticket.assignTo(assignee);
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse resolveTicket(long ticketID, String summary, long actorID){
        Ticket ticket = ticketRepository.findByTicketId(ticketID).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));

        ticketEventService.recordEvent(ticketID, actorID, TicketEventType.RESOLVED, ticket.getResolutionSummary(), summary);
        ticket.resolve(summary);
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse startTicketProcessing(long ticketID, long actorID) {
        Ticket ticket = ticketRepository.findByTicketId(ticketID)
                .orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));
        if (ticket.getAssignee() == null || !ticket.getAssignee().getId().equals(actorID)) {
            throw new AccessDeniedException("Only the assigned agent can start this ticket.");
        }

        if (ticket.getTicketStatus() == TicketStatus.IN_QUEUE) {
            ticket.changeStatus(TicketStatus.FULFILLMENT);
            ticketEventService.recordEvent(ticketID, actorID, TicketEventType.STATUS_CHANGED,
                    TicketStatus.IN_QUEUE.name(), TicketStatus.FULFILLMENT.name());
        } else if (ticket.getTicketStatus() != TicketStatus.FULFILLMENT) {
            throw new IllegalStatusException("Only tickets in queue can be started.");
        }

        return TicketResponse.from(ticket);
    }


    @Transactional
    public TicketResponse cancelTicket(long ticketID, long actorID){
        Ticket ticket = ticketRepository.findByTicketId(ticketID).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketID + " not found."));

        ticketEventService.recordEvent(ticketID, actorID, TicketEventType.CLOSED, null, null);
        ticket.cancel();
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getAllTickets(Pageable pageable) {
        return ticketRepository.findAll(pageable).map(TicketResponse::from);
    }

    public TicketResponse getTicketById(Long ticketId) {
        Ticket t = ticketRepository.findByTicketId(ticketId).orElseThrow(() -> new TicketNotFoundException("Ticket with ID " + ticketId + " not found."));
        return TicketResponse.from(t);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getTicketsByStatus(TicketStatus status, Pageable pageable) {
        return ticketRepository.findByTicketStatus(status, pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getTicketsByUserId(Long userId, Pageable pageable) {
        return ticketRepository.findByCreatorId(userId, pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> searchTicketsByCreator(Long creatorId, TicketStatus status, TicketPriority priority,
                                                       String search, Pageable pageable) {
        return ticketRepository.findAll(creatorFilter(creatorId, status, priority, search), pageable)
                .map(TicketResponse::from);
    }

    /** Per-status totals for the creator's tickets, honouring the priority and search filters. */
    @Transactional(readOnly = true)
    public Map<TicketStatus, Long> countTicketsByCreator(Long creatorId, TicketPriority priority, String search) {
        Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            counts.put(status, ticketRepository.count(creatorFilter(creatorId, status, priority, search)));
        }
        return counts;
    }

    private static Specification<Ticket> creatorFilter(Long creatorId, TicketStatus status, TicketPriority priority,
                                                       String search) {
        String term = search == null ? "" : search.trim().toLowerCase();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("creator").get("id"), creatorId));
            if (status != null) {
                predicates.add(cb.equal(root.get("ticketStatus"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("ticketPriority"), priority));
            }
            if (!term.isEmpty()) {
                String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("ticketNumber")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getTicketsByAssigneeId(Long assigneeId, Pageable pageable) {
        return ticketRepository.findByAssigneeId(assigneeId, pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getTicketsByUserIdAndStatus(Long userId, TicketStatus status, Pageable pageable) {
        return ticketRepository.findByCreatorIdAndTicketStatus(userId, status, pageable).map(TicketResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getTicketsByAssigneeIdAndStatus(Long assigneeId, TicketStatus status, Pageable pageable) {
        return ticketRepository.findByAssigneeIdAndTicketStatus(assigneeId, status, pageable).map(TicketResponse::from);
    }

}
