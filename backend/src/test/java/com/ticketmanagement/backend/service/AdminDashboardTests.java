package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.enums.TicketType;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.seed.enabled=false")
@Transactional
class AdminDashboardTests {

    @Autowired private AdminDashboardService dashboard;
    @Autowired private TicketRepository tickets;
    @Autowired private UserRepository users;

    @Test
    @WithMockUser(roles = "ADMIN")
    void countsAllRowsSeparatelyFromTheRecentPageAndExcludesClosedWorkAndInactiveAgents() {
        var before = dashboard.getSummary();
        AppUser creator = account(Role.USER, false);
        AppUser agent = account(Role.SUPPORT_AGENT, true);
        account(Role.SUPPORT_AGENT, false);
        account(Role.ADMIN, true);
        var added = new ArrayList<Ticket>();
        // Tied creation times also verify the deterministic ticketId tie-breaker.
        Instant createdAt = Instant.parse("2100-01-01T09:00:00Z");
        for (int index = 0; index < 7; index++) {
            Ticket ticket = new Ticket();
            ticket.setTicketNumber("DASH-TEST-" + UUID.randomUUID());
            ticket.setTitle("Dashboard test " + index);
            ticket.setDescription("Test statistics with more rows than the recent ticket limit.");
            ticket.setCreator(creator);
            ticket.setAssignee(index == 0 || index == 6 ? null : agent);
            ticket.setTicketStatus(index == 0 ? TicketStatus.IN_QUEUE
                    : index < 5 ? TicketStatus.FULFILLMENT
                    : index == 5 ? TicketStatus.RESOLVED : TicketStatus.CANCELLED);
            ticket.setTicketPriority(TicketPriority.HIGH);
            ticket.setTicketType(TicketType.INCIDENT);
            ticket.setCreatedAt(createdAt);
            ticket.setUpdatedAt(createdAt);
            added.add(tickets.save(ticket));
        }
        tickets.flush();
        var result = dashboard.getSummary();

        assertThat(result.totalTickets()).isEqualTo(before.totalTickets() + 7);
        assertThat(result.ticketsByStatus().get(TicketStatus.IN_QUEUE))
                .isEqualTo(before.ticketsByStatus().get(TicketStatus.IN_QUEUE) + 1);
        assertThat(result.ticketsByStatus().get(TicketStatus.FULFILLMENT))
                .isEqualTo(before.ticketsByStatus().get(TicketStatus.FULFILLMENT) + 4);
        assertThat(result.openTicketsByPriority().get(TicketPriority.HIGH))
                .isEqualTo(before.openTicketsByPriority().get(TicketPriority.HIGH) + 5);
        assertThat(result.unassignedOpenTickets()).isEqualTo(before.unassignedOpenTickets() + 1);
        assertThat(result.totalAccounts()).isEqualTo(before.totalAccounts() + 4);
        assertThat(result.regularUsers()).isEqualTo(before.regularUsers() + 1);
        assertThat(result.supportAgents()).isEqualTo(before.supportAgents() + 2);
        assertThat(result.activeSupportAgents()).isEqualTo(before.activeSupportAgents() + 1);
        Collections.reverse(added);
        assertThat(result.recentTickets()).extracting(TicketResponse::ticketId)
                .containsExactlyElementsOf(added.subList(0, 5).stream().map(Ticket::getTicketId).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_SUPPORT_AGENT"})
    void serviceAlsoRejectsNonAdministrators(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test@example.com", null, List.of(new SimpleGrantedAuthority(role))));
        try {
            assertThatThrownBy(dashboard::getSummary).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AppUser account(Role role, boolean active) {
        AppUser account = new AppUser(UUID.randomUUID() + "@dashboard.test", "test-only-hash", role);
        if (!active) account.deactivate();
        return users.save(account);
    }
}
