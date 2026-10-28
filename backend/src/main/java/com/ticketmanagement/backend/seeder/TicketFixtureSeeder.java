package com.ticketmanagement.backend.seeder;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@RequiredArgsConstructor
public class TicketFixtureSeeder {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CommentSeeder commentSeeder;
    private final TicketEventSeeder eventSeeder;

    /** Inserts one complete fixture atomically, or leaves an existing ticket and all its children untouched. */
    @Transactional
    public void seed(TicketSeeder.Fixture fixture) {
        if (ticketRepository.existsByTicketNumber(fixture.ticketNumber())) {
            return;
        }
        AppUser creator = requireUser(fixture.creatorEmail(), Role.USER);
        AppUser admin = requireUser(TicketSeeder.ADMIN, Role.ADMIN);
        AppUser initialAgent = requireAssignableAgent(fixture.initialAgentEmail());
        AppUser finalAgent = fixture.reassignedAgentEmail() == null
                ? initialAgent : requireAssignableAgent(fixture.reassignedAgentEmail());
        Instant timestamp = Instant.parse("2026-01-01T09:00:00Z").plusSeconds(fixture.number() * 86400L);

        Ticket ticket = new Ticket();
        ticket.setTicketNumber(fixture.ticketNumber());
        ticket.setTitle(fixture.title());
        ticket.setDescription(fixture.description());
        ticket.setTicketType(fixture.type());
        ticket.setTicketPriority(fixture.priority());
        ticket.setTicketStatus(TicketStatus.IN_QUEUE);
        ticket.setCreator(creator);
        ticket.setCreatedAt(timestamp);
        ticket.setUpdatedAt(timestamp);
        ticketRepository.save(ticket);
        eventSeeder.record(ticket, creator, TicketEventType.CREATED, null, null, timestamp);

        timestamp = timestamp.plusSeconds(60);
        ticket.assignTo(initialAgent);
        ticket.setUpdatedAt(timestamp);
        eventSeeder.record(ticket, admin, TicketEventType.ASSIGNED,
                null, initialAgent.getId().toString(), timestamp);
        timestamp = timestamp.plusSeconds(1);
        ticket.changeStatus(TicketStatus.FULFILLMENT);
        ticket.setUpdatedAt(timestamp);
        eventSeeder.record(ticket, admin, TicketEventType.STATUS_CHANGED,
                TicketStatus.IN_QUEUE.name(), TicketStatus.FULFILLMENT.name(), timestamp);

        if (!initialAgent.getId().equals(finalAgent.getId())) {
            timestamp = timestamp.plusSeconds(60);
            ticket.assignTo(finalAgent);
            ticket.setUpdatedAt(timestamp);
            eventSeeder.record(ticket, admin, TicketEventType.REASSIGNED,
                    initialAgent.getId().toString(), finalAgent.getId().toString(), timestamp);
        }

        for (TicketSeeder.SeedComment comment : fixture.comments()) {
            timestamp = timestamp.plusSeconds(60);
            AppUser author = requireUser(comment.authorEmail(), expectedRole(comment.authorEmail(), fixture));
            commentSeeder.seed(ticket, author, comment.content(), timestamp);
        }
        if (fixture.resolution() != null) {
            timestamp = timestamp.plusSeconds(60);
            ticket.resolve(fixture.resolution());
            ticket.setUpdatedAt(timestamp);
            eventSeeder.record(ticket, finalAgent, TicketEventType.RESOLVED,
                    null, fixture.resolution(), timestamp);
        } else if (fixture.cancelled()) {
            timestamp = timestamp.plusSeconds(60);
            ticket.changeStatus(TicketStatus.CANCELLED);
            ticket.setUpdatedAt(timestamp);
            eventSeeder.record(ticket, admin, TicketEventType.STATUS_CHANGED,
                    TicketStatus.FULFILLMENT.name(), TicketStatus.CANCELLED.name(), timestamp);
        }
    }

    private Role expectedRole(String email, TicketSeeder.Fixture fixture) {
        if (email.equals(TicketSeeder.ADMIN)) {
            return Role.ADMIN;
        }
        return email.equals(fixture.creatorEmail()) ? Role.USER : Role.SUPPORT_AGENT;
    }

    // Inactive agents must not receive assignments, so the fixture fails instead of reactivating them.
    private AppUser requireAssignableAgent(String email) {
        AppUser agent = requireUser(email, Role.SUPPORT_AGENT);
        if (!agent.isActive()) {
            throw new IllegalStateException("Seed agent " + email + " is inactive and cannot be assigned tickets");
        }
        return agent;
    }

    private AppUser requireUser(String email, Role expectedRole) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Required seed user is unavailable: " + email));

        if (user.getRole() != expectedRole) {
            throw new IllegalStateException("Seed user " + email + " must have role " + expectedRole
                    + " but has " + user.getRole());
        }
        return user;
    }
}
