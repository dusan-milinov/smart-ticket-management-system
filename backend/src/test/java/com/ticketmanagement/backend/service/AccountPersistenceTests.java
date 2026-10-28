package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.auth.ChangePasswordRequest;
import com.ticketmanagement.backend.dto.auth.LoginRequest;
import com.ticketmanagement.backend.dto.auth.RegisterRequest;
import com.ticketmanagement.backend.dto.auth.RegisterSupportAgentRequest;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.enums.TicketType;
import com.ticketmanagement.backend.exception.InactiveAssigneeException;
import com.ticketmanagement.backend.exception.UserAlreadyExistsException;
import com.ticketmanagement.backend.exception.UserDeactivationException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Comment;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.model.TicketEvent;
import com.ticketmanagement.backend.repository.CommentRepository;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import com.ticketmanagement.backend.security.ActiveAccountJwtAuthenticationConverter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs against the configured MySQL schema; every test rolls back its writes. */
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AccountPersistenceTests {

    private static final String PASSWORD = "Original-password-1";

    @Autowired private UserService service;
    @Autowired private TicketService ticketService;
    @Autowired private UserRepository users;
    @Autowired private TicketRepository tickets;
    @Autowired private CommentRepository comments;
    @Autowired private TicketEventRepository history;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtDecoder decoder;
    @Autowired private ActiveAccountJwtAuthenticationConverter authenticationConverter;
    @Autowired private MockMvc mvc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passwordChangePersistsAndRevokesPreviouslyIssuedTokens() {
        String email = uniqueEmail();
        service.register(new RegisterRequest(email, PASSWORD));
        String token = service.login(new LoginRequest(email, PASSWORD)).token();
        SecurityContextHolder.getContext().setAuthentication(authenticationConverter.convert(decoder.decode(token)));

        service.changeOwnPassword(new ChangePasswordRequest(PASSWORD, "Replacement-password-2"));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> service.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authenticationConverter.convert(decoder.decode(token)))
                .isInstanceOf(InvalidBearerTokenException.class);
        String newToken = service.login(new LoginRequest(email, "Replacement-password-2")).token();
        assertThat(authenticationConverter.convert(decoder.decode(newToken)).getName()).isEqualTo(email);
        AppUser stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.USER);
        assertThat(stored.getTokenVersion()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void agentRegistrationPersistsNormalizedEmailAndPreventsDuplicateAccount() {
        String email = uniqueEmail();
        var response = service.registerSupportAgent(
                new RegisterSupportAgentRequest(email.toUpperCase(Locale.ROOT), "Agent-password-1"));
        entityManager.flush();
        entityManager.clear();

        var stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.getId()).isEqualTo(response.id());
        assertThat(stored.getRole()).isEqualTo(Role.SUPPORT_AGENT);
        assertThat(stored.isActive()).isTrue();
        assertThat(response.active()).isTrue();
        assertThat(service.login(new LoginRequest(email, "Agent-password-1")).token()).isNotBlank();
        assertThatThrownBy(() -> service.registerSupportAgent(
                new RegisterSupportAgentRequest(email, "Different-password-2")))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void deactivatedAccountCannotLoginOrUseUnexpiredJwt() {
        String email = uniqueEmail();
        Long id = service.register(new RegisterRequest(email, PASSWORD)).id();
        String token = service.login(new LoginRequest(email, PASSWORD)).token();
        actAsAdmin(account(Role.ADMIN));

        var response = service.deactivateUser(id);
        entityManager.flush();
        entityManager.clear();

        assertThat(response.active()).isFalse();
        assertThat(users.findById(id).orElseThrow().isActive()).isFalse();
        assertThatThrownBy(() -> service.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOf(DisabledException.class);
        assertThatThrownBy(() -> authenticationConverter.convert(decoder.decode(token)))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void deactivationPreservesRowsAndReferencesAndIsIdempotent() {
        AppUser creator = account(Role.USER);
        AppUser agent = account(Role.SUPPORT_AGENT);
        Ticket ticket = ticket(creator, agent, TicketStatus.RESOLVED);
        Comment comment = comments.save(new Comment(null, "Thanks, it works now.", Instant.now(), creator, ticket));
        TicketEvent event = new TicketEvent(null, TicketEventType.RESOLVED, null, "Fixed", ticket, agent);
        history.save(event);
        actAsAdmin(account(Role.ADMIN));

        service.deactivateUser(creator.getId());
        service.deactivateUser(agent.getId());
        var repeated = service.deactivateUser(agent.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(repeated.active()).isFalse();
        Ticket stored = tickets.findById(ticket.getTicketId()).orElseThrow();
        assertThat(stored.getCreator().getId()).isEqualTo(creator.getId());
        assertThat(stored.getAssignee().getId()).isEqualTo(agent.getId());
        assertThat(stored.getCreator().isActive()).isFalse();
        assertThat(stored.getAssignee().isActive()).isFalse();
        assertThat(comments.findById(comment.getId()).orElseThrow().getAuthor().getId()).isEqualTo(creator.getId());
        assertThat(history.findById(event.getId()).orElseThrow().getInitiator().getId()).isEqualTo(agent.getId());
        assertThat(users.findById(creator.getId())).isPresent();
        assertThat(users.findById(agent.getId())).isPresent();
    }

    @Test
    void agentWithUnresolvedTicketsIsNotDeactivated() {
        AppUser agent = account(Role.SUPPORT_AGENT);
        ticket(account(Role.USER), agent, TicketStatus.FULFILLMENT);
        actAsAdmin(account(Role.ADMIN));

        assertThatThrownBy(() -> service.deactivateUser(agent.getId()))
                .isInstanceOf(UserDeactivationException.class)
                .hasMessage("User has 1 unresolved assigned ticket(s); reassign them before deactivation");
        entityManager.flush();
        entityManager.clear();
        assertThat(users.findById(agent.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void inactiveAgentCannotReceiveReassignedTicket() {
        AppUser admin = account(Role.ADMIN);
        AppUser current = account(Role.SUPPORT_AGENT);
        AppUser inactive = account(Role.SUPPORT_AGENT);
        AppUser replacement = account(Role.SUPPORT_AGENT);
        actAsAdmin(admin);
        inactive.deactivate();
        Ticket ticket = ticket(account(Role.USER), current, TicketStatus.FULFILLMENT);
        entityManager.flush();

        assertThatThrownBy(() -> ticketService.reassign(ticket.getTicketId(), inactive.getId()))
                .isInstanceOf(InactiveAssigneeException.class);
        assertThat(ticket.getAssignee().getId()).isEqualTo(current.getId());
        assertThat(history.findAllByTicket(ticket)).isEmpty();

        ticketService.reassign(ticket.getTicketId(), replacement.getId());
        entityManager.flush();
        entityManager.clear();
        Ticket stored = tickets.findById(ticket.getTicketId()).orElseThrow();
        assertThat(stored.getAssignee().getId()).isEqualTo(replacement.getId());
        assertThat(stored.getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
        assertThat(history.findAllByTicket(stored)).hasSize(2)
                .filteredOn(event -> event.getEventType() == TicketEventType.REASSIGNED).singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(TicketEventType.REASSIGNED);
                    assertThat(event.getOldValue()).isEqualTo(current.getId().toString());
                    assertThat(event.getNewValue()).isEqualTo(replacement.getId().toString());
                });
        assertThat(history.findAllByTicket(stored))
                .filteredOn(event -> event.getEventType() == TicketEventType.STATUS_CHANGED).singleElement()
                .satisfies(event -> {
                    assertThat(event.getOldValue()).isEqualTo(TicketStatus.FULFILLMENT.name());
                    assertThat(event.getNewValue()).isEqualTo(TicketStatus.IN_QUEUE.name());
                });

        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                replacement.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_SUPPORT_AGENT"))));
        ticketService.startTicketProcessing(stored.getTicketId());
        entityManager.flush();
        entityManager.clear();
        assertThat(tickets.findById(stored.getTicketId()).orElseThrow().getTicketStatus())
                .isEqualTo(TicketStatus.FULFILLMENT);
    }

    @Test
    void schemaDefaultsMarkRowsWithoutExplicitValuesActive() {
        Object[] column = (Object[]) entityManager.createNativeQuery("""
                        select is_nullable, column_default from information_schema.columns
                        where table_schema = database() and table_name = 'app_user' and column_name = 'active'""")
                .getSingleResult();
        assertThat(column[0]).isEqualTo("NO");
        assertThat(column[1]).isNotNull();

        String email = uniqueEmail();
        entityManager.createNativeQuery("insert into app_user (email, password, role) values (?, ?, 'USER')")
                .setParameter(1, email).setParameter(2, "unused-test-hash").executeUpdate();
        AppUser stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.getTokenVersion()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ticketFiltersApplyBeforePagingAndKeepCreatorAssigneeAndRelatedScopes() {
        actAsAdmin(account(Role.ADMIN));
        AppUser creator = account(Role.USER);
        AppUser agent = account(Role.SUPPORT_AGENT);
        AppUser other = account(Role.USER);
        AppUser otherAgent = account(Role.SUPPORT_AGENT);
        Ticket older = ticket(creator, agent, TicketStatus.IN_QUEUE);
        older.setTitle("VPN older");
        older.setTicketPriority(TicketPriority.HIGH);
        older.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        Ticket newer = ticket(creator, agent, TicketStatus.IN_QUEUE);
        newer.setTitle("VPN newer");
        newer.setTicketPriority(TicketPriority.HIGH);
        newer.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z"));
        Ticket wrongStatus = ticket(creator, agent, TicketStatus.RESOLVED);
        wrongStatus.setTitle("VPN resolved");
        wrongStatus.setTicketPriority(TicketPriority.HIGH);
        Ticket wrongPriority = ticket(creator, agent, TicketStatus.IN_QUEUE);
        wrongPriority.setTitle("VPN low");
        wrongPriority.setTicketPriority(TicketPriority.LOW);
        Ticket unrelated = ticket(other, otherAgent, TicketStatus.IN_QUEUE);
        unrelated.setTitle("VPN unrelated");
        unrelated.setTicketPriority(TicketPriority.HIGH);
        Ticket assignedOnly = ticket(other, agent, TicketStatus.IN_QUEUE);
        assignedOnly.setTitle("VPN assigned only");
        assignedOnly.setTicketPriority(TicketPriority.HIGH);
        entityManager.flush();

        var ascending = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("ticketId"));
        var firstPage = PageRequest.of(0, 1, ascending);
        var creatorPage = ticketService.searchTicketsByCreator(
                creator.getId(), TicketStatus.IN_QUEUE, TicketPriority.HIGH, "  vPn  ", firstPage);
        assertThat(creatorPage.getTotalElements()).isEqualTo(2);
        assertThat(creatorPage.getContent()).extracting(t -> t.ticketId()).containsExactly(older.getTicketId());
        var secondPage = ticketService.searchTicketsByCreator(
                creator.getId(), TicketStatus.IN_QUEUE, TicketPriority.HIGH, "vpn", PageRequest.of(1, 1, ascending));
        assertThat(secondPage.getContent()).extracting(t -> t.ticketId()).containsExactly(newer.getTicketId());
        var assignedPage = ticketService.searchTicketsByAssignee(
                agent.getId(), TicketStatus.IN_QUEUE, TicketPriority.HIGH, "vpn", PageRequest.of(0, 10, ascending));
        assertThat(assignedPage.getContent()).extracting(t -> t.ticketId())
                .containsExactly(older.getTicketId(), newer.getTicketId(), assignedOnly.getTicketId());
        var relatedPage = service.getUserTickets(
                agent.getId(), TicketStatus.IN_QUEUE, TicketPriority.HIGH, "vpn", PageRequest.of(0, 10, ascending));
        assertThat(relatedPage.getContent()).extracting(t -> t.ticketId())
                .containsExactly(older.getTicketId(), newer.getTicketId(), assignedOnly.getTicketId());
        var descending = ticketService.searchTicketsByAssignee(
                agent.getId(), TicketStatus.IN_QUEUE, TicketPriority.HIGH, "vpn",
                PageRequest.of(0, 10, ascending.reverse()));
        assertThat(descending.getContent()).extracting(t -> t.ticketId())
                .containsExactly(assignedOnly.getTicketId(), newer.getTicketId(), older.getTicketId());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void relatedTicketSearchIncludesUnassignedCreatedTicketsAndEscapesWildcards() {
        actAsAdmin(account(Role.ADMIN));
        AppUser creator = account(Role.USER);
        Ticket match = ticket(creator, null, TicketStatus.IN_QUEUE);
        match.setTitle("VPN_100% outage");
        Ticket nonMatch = ticket(creator, null, TicketStatus.IN_QUEUE);
        nonMatch.setTitle("VPNx1000 outage");
        entityManager.flush();

        var page = service.getUserTickets(creator.getId(), TicketStatus.IN_QUEUE, null,
                "VPN_100%", PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(t -> t.ticketId()).containsExactly(match.getTicketId());
        var byNumber = ticketService.searchTicketsByCreator(
                creator.getId(), null, null, match.getTicketNumber(), PageRequest.of(0, 10));
        assertThat(byNumber.getContent()).extracting(t -> t.ticketId()).containsExactly(match.getTicketId());
    }

    @Test
    void assignedEndpointSortsAllTicketsAndFiltersBeforePaginationAndCounts() throws Exception {
        AppUser creator = account(Role.USER);
        AppUser agent = account(Role.SUPPORT_AGENT);
        AppUser otherAgent = account(Role.SUPPORT_AGENT);
        Ticket older = ticket(creator, agent, TicketStatus.IN_QUEUE);
        older.setTitle("VPN older");
        older.setTicketPriority(TicketPriority.HIGH);
        older.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        Ticket newer = ticket(creator, agent, TicketStatus.RESOLVED);
        newer.setTitle("VPN newer");
        newer.setTicketPriority(TicketPriority.HIGH);
        newer.setCreatedAt(Instant.parse("2026-02-01T00:00:00Z"));
        Ticket unrelated = ticket(creator, otherAgent, TicketStatus.IN_QUEUE);
        unrelated.setTitle("VPN unrelated");
        unrelated.setTicketPriority(TicketPriority.HIGH);
        entityManager.flush();

        var auth = user(agent.getEmail()).roles("SUPPORT_AGENT");
        mvc.perform(get("/tickets/assigned").with(auth)
                        .param("size", "1").param("sort", "createdAt,desc", "ticketId,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(newer.getTicketId()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/tickets/assigned").with(auth)
                        .param("size", "1").param("sort", "createdAt,asc", "ticketId,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(older.getTicketId()));
        mvc.perform(get("/tickets/assigned").with(auth).param("page", "1")
                        .param("size", "1").param("sort", "createdAt,asc", "ticketId,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(newer.getTicketId()));
        mvc.perform(get("/tickets/assigned").with(auth)
                        .param("status", "IN_QUEUE").param("priority", "HIGH").param("search", "vpn")
                        .param("size", "1").param("sort", "createdAt,asc", "ticketId,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(older.getTicketId()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/tickets/assigned/status-counts").with(auth)
                        .param("priority", "HIGH").param("search", "vpn").param("status", "IN_QUEUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.IN_QUEUE").value(1))
                .andExpect(jsonPath("$.FULFILLMENT").value(0))
                .andExpect(jsonPath("$.RESOLVED").value(1))
                .andExpect(jsonPath("$.CANCELLED").value(0));
        mvc.perform(get("/tickets/assigned/status-counts").with(auth).param("search", "no matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.IN_QUEUE").value(0))
                .andExpect(jsonPath("$.RESOLVED").value(0));
    }

    private AppUser account(Role role) {
        return users.save(new AppUser(uniqueEmail(), "unused-test-hash", role));
    }

    private Ticket ticket(AppUser creator, AppUser assignee, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setTicketNumber("TKT-T-" + UUID.randomUUID().toString().substring(0, 8));
        ticket.setTitle("Printer offline");
        ticket.setDescription("The office printer does not respond.");
        ticket.setTicketStatus(status);
        ticket.setTicketPriority(TicketPriority.values()[0]);
        ticket.setTicketType(TicketType.values()[0]);
        ticket.setCreator(creator);
        ticket.setAssignee(assignee);
        return tickets.save(ticket);
    }

    private void actAsAdmin(AppUser admin) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                admin.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private String uniqueEmail() {
        return "account-test-" + UUID.randomUUID() + "@example.com";
    }
}
