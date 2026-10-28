package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Relationship checks shared by authenticated ticket, comment, and history operations. */
@Component
class TicketAccessPolicy {

    void requireRead(Ticket ticket, AppUser actor) {
        boolean allowed = switch (actor.getRole()) {
            case ADMIN -> true;
            case USER -> ticket.getCreator().getId().equals(actor.getId());
            case SUPPORT_AGENT -> isAssignedTo(ticket, actor);
        };
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this ticket.");
        }
    }

    void requireAssignedAgent(Ticket ticket, AppUser actor) {
        if (actor.getRole() != Role.SUPPORT_AGENT || !isAssignedTo(ticket, actor)) {
            throw new AccessDeniedException("Only the assigned agent can start this ticket.");
        }
    }

    void requireCreatorScope(Long creatorId, AppUser actor) {
        if (actor.getRole() != Role.ADMIN
                && (actor.getRole() != Role.USER || !actor.getId().equals(creatorId))) {
            throw new AccessDeniedException("You can only list your own tickets.");
        }
    }

    void requireAssigneeScope(Long assigneeId, AppUser actor) {
        if (actor.getRole() != Role.ADMIN
                && (actor.getRole() != Role.SUPPORT_AGENT || !actor.getId().equals(assigneeId))) {
            throw new AccessDeniedException("You can only list tickets assigned to you.");
        }
    }

    private boolean isAssignedTo(Ticket ticket, AppUser actor) {
        return ticket.getAssignee() != null && ticket.getAssignee().getId().equals(actor.getId());
    }
}
