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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against the configured MySQL schema; every test rolls back its writes. */
@SpringBootTest(properties = "app.seed.enabled=false")
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
        inactive.deactivate();
        Ticket ticket = ticket(account(Role.USER), current, TicketStatus.FULFILLMENT);
        entityManager.flush();

        assertThatThrownBy(() -> ticketService.reassign(ticket.getTicketId(), inactive.getId(), admin.getId()))
                .isInstanceOf(InactiveAssigneeException.class);
        assertThat(ticket.getAssignee().getId()).isEqualTo(current.getId());
        assertThat(history.findAllByTicket(ticket)).isEmpty();

        ticketService.reassign(ticket.getTicketId(), replacement.getId(), admin.getId());
        entityManager.flush();
        entityManager.clear();
        Ticket stored = tickets.findById(ticket.getTicketId()).orElseThrow();
        assertThat(stored.getAssignee().getId()).isEqualTo(replacement.getId());
        assertThat(history.findAllByTicket(stored)).singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(TicketEventType.REASSIGNED);
                    assertThat(event.getOldValue()).isEqualTo(current.getId().toString());
                    assertThat(event.getNewValue()).isEqualTo(replacement.getId().toString());
                });
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
