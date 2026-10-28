package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.comment.CreateCommentRequest;
import com.ticketmanagement.backend.dto.ticket.CreateTicketRequest;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.enums.TicketType;
import com.ticketmanagement.backend.exception.IllegalStatusException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.CommentRepository;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses proxied services and real persistence; all writes are rolled back. */
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Transactional
class TicketAuthorizationTests {

    @Autowired private TicketService service;
    @Autowired private CommentService commentService;
    @Autowired private TicketEventService eventService;
    @Autowired private TicketRepository tickets;
    @Autowired private UserRepository users;
    @Autowired private CommentRepository comments;
    @Autowired private TicketEventRepository events;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mvc;
    @MockitoSpyBean private TicketEventRecorder recorder;

    enum Identity { CREATOR, OTHER_USER, ASSIGNED_AGENT, OTHER_AGENT, ADMIN, AGENT_CREATOR, USER_ASSIGNEE }
    enum Mutation { CREATE, COMMENT, REASSIGN, RESOLVE, CANCEL, CANCEL_OWN, START }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Identity.class)
    void detailsCommentsAndHistoryUseTheSameRelationshipPolicy(Identity identity) {
        Fixture fixture = fixture(identity);
        Ticket ticket = fixture.ticket();
        authenticate(fixture.actor());

        if (canRead(identity)) {
            assertThat(service.getTicketById(ticket.getTicketId()).ticketId()).isEqualTo(ticket.getTicketId());
            assertThat(commentService.getAllTicketComments(ticket.getTicketId())).isEmpty();
            assertThat(eventService.getAllTicketEvents(ticket.getTicketId())).isEmpty();
            commentService.addComment(new CreateCommentRequest("Checking the network connection.", ticket.getTicketId()));
            entityManager.flush();
            assertThat(commentService.getAllTicketComments(ticket.getTicketId())).hasSize(1);
            assertThat(eventService.getAllTicketEvents(ticket.getTicketId())).hasSize(1);
            assertThat(comments.findAllByTicket(ticket)).singleElement().satisfies(comment ->
                    assertThat(comment.getAuthor().getId()).isEqualTo(fixture.actor().getId()));
            assertThat(events.findAllByTicket(ticket)).singleElement().satisfies(event -> {
                assertThat(event.getInitiator().getId()).isEqualTo(fixture.actor().getId());
                assertThat(event.getEventType()).isEqualTo(TicketEventType.COMMENT_ADDED);
            });
        } else {
            denied(() -> service.getTicketById(ticket.getTicketId()));
            denied(() -> commentService.getAllTicketComments(ticket.getTicketId()));
            denied(() -> eventService.getAllTicketEvents(ticket.getTicketId()));
            denied(() -> commentService.addComment(new CreateCommentRequest("Forbidden", ticket.getTicketId())));
            assertThat(comments.findAllByTicket(ticket)).isEmpty();
            assertThat(events.findAllByTicket(ticket)).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(Identity.class)
    void onlyAssignedAgentsAndAdministratorsCanResolve(Identity identity) {
        Fixture fixture = fixture(identity);
        authenticate(fixture.actor());
        Ticket ticket = fixture.ticket();

        if (identity == Identity.ASSIGNED_AGENT || identity == Identity.ADMIN) {
            var result = service.resolveTicket(ticket.getTicketId(), "Replaced the damaged network cable.");
            assertThat(result.ticketStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(result.resolutionSummary()).isEqualTo("Replaced the damaged network cable.");
            assertThat(events.findAllByTicket(ticket)).singleElement().satisfies(event -> {
                assertThat(event.getEventType()).isEqualTo(TicketEventType.RESOLVED);
                assertThat(event.getInitiator().getId()).isEqualTo(fixture.actor().getId());
            });
        } else {
            denied(() -> service.resolveTicket(ticket.getTicketId(), "Forged resolution"));
            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
            assertThat(events.findAllByTicket(ticket)).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(Identity.class)
    void onlyAdministratorsCanReassign(Identity identity) {
        Fixture fixture = fixture(identity);
        authenticate(fixture.actor());
        Ticket ticket = fixture.ticket();

        if (identity == Identity.ADMIN) {
            service.reassign(ticket.getTicketId(), fixture.otherAgent().getId());
            assertThat(ticket.getAssignee().getId()).isEqualTo(fixture.otherAgent().getId());
            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.FULFILLMENT);
            assertThat(events.findAllByTicket(ticket)).singleElement().satisfies(event -> {
                assertThat(event.getEventType()).isEqualTo(TicketEventType.REASSIGNED);
                assertThat(event.getInitiator().getId()).isEqualTo(fixture.actor().getId());
            });
        } else {
            denied(() -> service.reassign(ticket.getTicketId(), fixture.otherAgent().getId()));
            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
            assertThat(events.findAllByTicket(ticket)).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(Identity.class)
    void onlyTheRegularUserCreatorOrAnAdministratorCanCancel(Identity identity) {
        Fixture fixture = fixture(identity);
        authenticate(fixture.actor());
        Ticket ticket = fixture.ticket();

        if (identity == Identity.CREATOR || identity == Identity.ADMIN) {
            assertThat(service.cancelTicket(ticket.getTicketId()).ticketStatus()).isEqualTo(TicketStatus.CANCELLED);
            assertThat(events.findAllByTicket(ticket)).singleElement().satisfies(event -> {
                assertThat(event.getEventType()).isEqualTo(TicketEventType.CLOSED);
                assertThat(event.getInitiator().getId()).isEqualTo(fixture.actor().getId());
            });
        } else {
            denied(() -> service.cancelTicket(ticket.getTicketId()));
            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
            assertThat(events.findAllByTicket(ticket)).isEmpty();
        }
    }

    @Test
    void regularUsersCanAlsoCancelTheirTicketsWhileInFulfillment() {
        Fixture fixture = fixture(Identity.CREATOR);
        fixture.ticket().setTicketStatus(TicketStatus.FULFILLMENT);
        authenticate(fixture.actor());

        assertThat(service.cancelTicket(fixture.ticket().getTicketId()).ticketStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(events.findAllByTicket(fixture.ticket())).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"RESOLVED", "CANCELLED"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void neitherCreatorsNorAdministratorsCanCancelTerminalTickets(TicketStatus ticketStatus) {
        List<Long> before = counts();
        for (Identity identity : List.of(Identity.CREATOR, Identity.ADMIN)) {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Fixture fixture = fixture(identity);
                fixture.ticket().setTicketStatus(ticketStatus);
                authenticate(fixture.actor());
                service.cancelTicket(fixture.ticket().getTicketId());
            })).isInstanceOf(IllegalStatusException.class);
            assertThat(counts()).isEqualTo(before);
        }
    }

    @ParameterizedTest
    @EnumSource(Identity.class)
    void startingProcessingRemainsAssignedAgentOnly(Identity identity) {
        Fixture fixture = fixture(identity);
        authenticate(fixture.actor());
        Ticket ticket = fixture.ticket();
        if (identity == Identity.ASSIGNED_AGENT) {
            service.startTicketProcessing(ticket.getTicketId());
            service.startTicketProcessing(ticket.getTicketId());
            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.FULFILLMENT);
            assertThat(events.findAllByTicket(ticket)).hasSize(1);
        } else {
            denied(() -> service.startTicketProcessing(ticket.getTicketId()));
            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
            assertThat(events.findAllByTicket(ticket)).isEmpty();
        }
    }

    @Test
    void unassignedTicketsAreVisibleToTheirCreatorAndAdministratorOnly() {
        Fixture fixture = fixture(Identity.CREATOR);
        fixture.ticket().setAssignee(null);
        authenticate(fixture.actor());
        assertThat(service.getTicketById(fixture.ticket().getTicketId())).isNotNull();
        authenticate(account(Role.ADMIN));
        assertThat(service.getTicketById(fixture.ticket().getTicketId())).isNotNull();
        authenticate(fixture.otherAgent());
        denied(() -> service.getTicketById(fixture.ticket().getTicketId()));
        denied(() -> commentService.getAllTicketComments(fixture.ticket().getTicketId()));
        denied(() -> eventService.getAllTicketEvents(fixture.ticket().getTicketId()));
        denied(() -> service.resolveTicket(fixture.ticket().getTicketId(), "Not assigned"));
        denied(() -> service.startTicketProcessing(fixture.ticket().getTicketId()));
    }

    @ParameterizedTest
    @EnumSource(value = Identity.class, names = {"CREATOR", "ASSIGNED_AGENT", "ADMIN"})
    void everyListSearchCountAndQueueChecksItsScope(Identity identity) {
        Fixture fixture = fixture(identity);
        authenticate(fixture.actor());
        Long creatorId = fixture.ticket().getCreator().getId();
        Long agentId = fixture.ticket().getAssignee().getId();
        var page = PageRequest.of(0, 10);
        List<Executable> creatorQueries = List.of(
                () -> service.getTicketsByUserId(creatorId, page),
                () -> service.getTicketsByUserIdAndStatus(creatorId, TicketStatus.IN_QUEUE, page),
                () -> service.searchTicketsByCreator(creatorId, null, null, null, page),
                () -> service.countTicketsByCreator(creatorId, null, null));
        List<Executable> agentQueries = List.of(
                () -> service.getTicketsByAssigneeId(agentId, page),
                () -> service.getTicketsByAssigneeIdAndStatus(agentId, TicketStatus.IN_QUEUE, page),
                () -> service.searchTicketsByAssignee(agentId, null, null, null, page),
                () -> service.countTicketsByAssignee(agentId, null, null),
                () -> service.getAssignedPriorityQueue(agentId));
        List<Executable> globalQueries = List.of(
                () -> service.getAllTickets(page),
                () -> service.getTicketsByStatus(TicketStatus.IN_QUEUE, page));
        checkQueries(creatorQueries, identity != Identity.ASSIGNED_AGENT);
        checkQueries(agentQueries, identity != Identity.CREATOR);
        checkQueries(globalQueries, identity == Identity.ADMIN);
        if (identity != Identity.ADMIN) {
            // Even empty and out-of-range queries must reject another account's ID.
            Long otherId = fixture.otherAgent().getId();
            denied(() -> service.getTicketsByUserId(otherId, page));
            denied(() -> service.getTicketsByUserIdAndStatus(otherId, TicketStatus.RESOLVED, page));
            denied(() -> service.searchTicketsByCreator(otherId, null, null, "no matches", page));
            denied(() -> service.countTicketsByCreator(otherId, null, "no matches"));
            denied(() -> service.getTicketsByAssigneeId(otherId, page));
            denied(() -> service.getTicketsByAssigneeIdAndStatus(otherId, TicketStatus.RESOLVED, page));
            denied(() -> service.searchTicketsByAssignee(otherId, null, null, "no matches", page));
            denied(() -> service.countTicketsByAssignee(otherId, null, "no matches"));
            denied(() -> service.getAssignedPriorityQueue(otherId));
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void creationKeepsEveryExistingRoleEligibleAndUsesTheAuthenticatedCreator(Role role) {
        prepareAssignmentLock();
        AppUser actor = account(role);
        account(Role.SUPPORT_AGENT);
        authenticate(actor);
        var created = service.createTicket(request());
        Ticket stored = tickets.findById(created.ticketId()).orElseThrow();
        assertThat(stored.getCreator().getId()).isEqualTo(actor.getId());
        assertThat(stored.getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
        assertThat(stored.getAssignee().getRole()).isEqualTo(Role.SUPPORT_AGENT);
        assertThat(stored.getAssignee().isActive()).isTrue();
        assertThat(events.findAllByTicket(stored)).hasSize(2).allSatisfy(event ->
                assertThat(event.getInitiator().getId()).isEqualTo(actor.getId()));
    }

    @Test
    void allUserFacingServiceMethodsRejectUnauthenticatedCalls() {
        SecurityContextHolder.clearContext();
        var page = PageRequest.of(0, 10);
        List<Executable> calls = List.of(
                () -> service.createTicket(request()),
                () -> service.reassign(1L, 2L),
                () -> service.resolveTicket(1L, "Summary"),
                () -> service.startTicketProcessing(1L),
                () -> service.cancelTicket(1L),
                () -> service.getAllTickets(page),
                () -> service.getTicketById(1L),
                () -> service.getTicketsByStatus(TicketStatus.IN_QUEUE, page),
                () -> service.getTicketsByUserId(1L, page),
                () -> service.searchTicketsByCreator(1L, null, null, null, page),
                () -> service.countTicketsByCreator(1L, null, null),
                () -> service.searchTicketsByAssignee(1L, null, null, null, page),
                () -> service.countTicketsByAssignee(1L, null, null),
                () -> service.getAssignedPriorityQueue(1L),
                () -> service.getTicketsByAssigneeId(1L, page),
                () -> service.getTicketsByUserIdAndStatus(1L, TicketStatus.IN_QUEUE, page),
                () -> service.getTicketsByAssigneeIdAndStatus(1L, TicketStatus.IN_QUEUE, page),
                () -> commentService.addComment(new CreateCommentRequest("Test", 1L)),
                () -> commentService.getAllTicketComments(1L),
                () -> eventService.getAllTicketEvents(1L));
        calls.forEach(call -> assertThatThrownBy(call::execute)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class));
    }

    @ParameterizedTest
    @EnumSource(Identity.class)
    void httpReadsAndCommentsEnforceServiceAuthorization(Identity identity) throws Exception {
        Fixture fixture = fixture(identity);
        var auth = user(fixture.actor().getEmail()).roles(fixture.actor().getRole().name());
        int expected = canRead(identity) ? 200 : 403;
        Long id = fixture.ticket().getTicketId();
        for (String path : List.of("/tickets/", "/comments/", "/ticket-events/")) {
            mvc.perform(get(path + id).with(auth)).andExpect(status().is(expected));
        }
        mvc.perform(post("/comments").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Connection checked.\",\"ticketId\":%d,\"actorId\":%d}".formatted(
                                id, fixture.otherAgent().getId())))
                .andExpect(status().is(expected));
        if (canRead(identity)) {
            assertThat(comments.findAllByTicket(fixture.ticket())).singleElement().satisfies(comment ->
                    assertThat(comment.getAuthor().getId()).isEqualTo(fixture.actor().getId()));
        }
    }

    @Test
    void httpCannotSpoofTheCreatorOrAnAssignedAgent() throws Exception {
        prepareAssignmentLock();
        Fixture fixture = fixture(Identity.OTHER_AGENT);
        mvc.perform(post("/tickets/add").with(user(fixture.actor().getEmail()).roles("SUPPORT_AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"VPN unavailable","description":"VPN disconnects on sign-in.",
                                 "ticketPriority":"HIGH","ticketType":"%s","creator":{"id":%d}}
                                """.formatted(TicketType.values()[0], fixture.ticket().getCreator().getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creatorId").value(fixture.actor().getId()));
        mvc.perform(patch("/tickets/" + fixture.ticket().getTicketId() + "/start")
                        .with(user(fixture.actor().getEmail()).roles("SUPPORT_AGENT"))
                        .param("actorId", fixture.ticket().getAssignee().getId().toString()))
                .andExpect(status().isForbidden());
        assertThat(fixture.ticket().getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
    }

    @ParameterizedTest
    @EnumSource(value = Identity.class, names = {"CREATOR", "OTHER_AGENT"})
    void httpMutationsCannotUseASuppliedActorToBypassAuthorization(Identity identity) throws Exception {
        Fixture fixture = fixture(identity);
        var auth = user(fixture.actor().getEmail()).roles(fixture.actor().getRole().name());
        String id = fixture.ticket().getTicketId().toString();
        for (String action : List.of("reassign", "resolve")) {
            mvc.perform(patch("/tickets/" + action).with(auth).param("ticketID", id)
                            .param("assigneeId", fixture.otherAgent().getId().toString())
                            .param("summary", "Unauthorized change")
                            .param("actorId", fixture.ticket().getAssignee().getId().toString()))
                    .andExpect(status().isForbidden());
        }
        assertThat(fixture.ticket().getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
        assertThat(events.findAllByTicket(fixture.ticket())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Identity.class)
    void httpCancellationChecksOwnershipAndIgnoresASuppliedActor(Identity identity) throws Exception {
        Fixture fixture = fixture(identity);
        var auth = user(fixture.actor().getEmail()).roles(fixture.actor().getRole().name());
        boolean allowed = identity == Identity.CREATOR || identity == Identity.ADMIN;
        mvc.perform(patch("/tickets/cancel").with(auth)
                        .param("ticketID", fixture.ticket().getTicketId().toString())
                        .param("actorId", fixture.ticket().getCreator().getId().toString()))
                .andExpect(status().is(allowed ? 200 : 403));
        if (allowed) {
            assertThat(fixture.ticket().getTicketStatus()).isEqualTo(TicketStatus.CANCELLED);
            assertThat(events.findAllByTicket(fixture.ticket())).singleElement().satisfies(event ->
                    assertThat(event.getInitiator().getId()).isEqualTo(fixture.actor().getId()));
        } else {
            assertThat(fixture.ticket().getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
            assertThat(events.findAllByTicket(fixture.ticket())).isEmpty();
        }
    }

    @Test
    void httpAdministratorMutationsRecordTheAuthenticatedActor() throws Exception {
        Fixture fixture = fixture(Identity.ADMIN);
        var auth = user(fixture.actor().getEmail()).roles("ADMIN");
        String id = fixture.ticket().getTicketId().toString();
        mvc.perform(patch("/tickets/reassign").with(auth).param("ticketID", id)
                        .param("assigneeId", fixture.otherAgent().getId().toString())
                        .param("actorId", fixture.ticket().getCreator().getId().toString()))
                .andExpect(status().isOk());
        mvc.perform(patch("/tickets/cancel").with(auth).param("ticketID", id)
                        .param("actorId", fixture.ticket().getCreator().getId().toString()))
                .andExpect(status().isOk());
        assertThat(events.findAllByTicket(fixture.ticket())).hasSize(2).allSatisfy(event ->
                assertThat(event.getInitiator().getId()).isEqualTo(fixture.actor().getId()));
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void eventFailureRollsBackTheWholeOwningTransaction(Mutation mutation) {
        List<Long> before = counts();
        TicketEventRecorder target = AopTestUtils.getUltimateTargetObject(recorder);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            throw new IllegalStateException("Simulated failure after writing history");
        }).when(target).recordEvent(anyLong(), anyLong(), any(), nullable(String.class), nullable(String.class));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Identity identity = switch (mutation) {
                case START -> Identity.ASSIGNED_AGENT;
                case CANCEL_OWN -> Identity.CREATOR;
                default -> Identity.ADMIN;
            };
            Fixture fixture = fixture(identity);
            authenticate(fixture.actor());
            Long id = fixture.ticket().getTicketId();
            switch (mutation) {
                case CREATE -> {
                    prepareAssignmentLock();
                    service.createTicket(request());
                }
                case COMMENT -> commentService.addComment(new CreateCommentRequest("Test", id));
                case REASSIGN -> service.reassign(id, fixture.otherAgent().getId());
                case RESOLVE -> service.resolveTicket(id, "Connection restored.");
                case CANCEL, CANCEL_OWN -> service.cancelTicket(id);
                case START -> service.startTicketProcessing(id);
            }
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simulated failure after writing history");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void internalEventRecordingCannotStartAnIndependentTransaction() {
        assertThatThrownBy(() -> recorder.recordEvent(1L, 1L, TicketEventType.CREATED, null, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private void checkQueries(List<Executable> queries, boolean allowed) {
        queries.forEach(query -> {
            if (allowed) {
                org.junit.jupiter.api.Assertions.assertDoesNotThrow(query);
            } else {
                denied(query);
            }
        });
    }

    private void denied(Executable call) {
        assertThatThrownBy(call::execute).isInstanceOf(AccessDeniedException.class);
    }

    private boolean canRead(Identity identity) {
        return identity == Identity.CREATOR || identity == Identity.ASSIGNED_AGENT || identity == Identity.ADMIN;
    }

    private Fixture fixture(Identity identity) {
        AppUser creator = account(Role.USER);
        AppUser agent = account(Role.SUPPORT_AGENT);
        AppUser otherAgent = account(Role.SUPPORT_AGENT);
        AppUser actor = switch (identity) {
            case CREATOR -> creator;
            case OTHER_USER, USER_ASSIGNEE -> account(Role.USER);
            case ASSIGNED_AGENT -> agent;
            case OTHER_AGENT, AGENT_CREATOR -> otherAgent;
            case ADMIN -> account(Role.ADMIN);
        };
        Ticket ticket = new Ticket();
        ticket.setTicketNumber("TKT-AUTH-" + UUID.randomUUID().toString().substring(0, 8));
        ticket.setTitle("Office network unavailable");
        ticket.setDescription("The wired network connection is unavailable in the office.");
        ticket.setTicketStatus(TicketStatus.IN_QUEUE);
        ticket.setTicketPriority(TicketPriority.HIGH);
        ticket.setTicketType(TicketType.values()[0]);
        ticket.setCreator(identity == Identity.AGENT_CREATOR ? actor : creator);
        ticket.setAssignee(identity == Identity.USER_ASSIGNEE ? actor : agent);
        return new Fixture(tickets.save(ticket), actor, otherAgent);
    }

    private AppUser account(Role role) {
        return users.save(new AppUser("authorization-" + UUID.randomUUID() + "@example.com", "unused-test-hash", role));
    }

    private void authenticate(AppUser actor) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                actor.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + actor.getRole().name()))));
    }

    private CreateTicketRequest request() {
        return new CreateTicketRequest("VPN unavailable", "VPN disconnects on sign-in.",
                TicketPriority.HIGH, TicketType.values()[0]);
    }

    private List<Long> counts() {
        return List.of(users.count(), tickets.count(), comments.count(), events.count());
    }

    private void prepareAssignmentLock() {
        entityManager.createNativeQuery("""
                insert into assignment_lock (id)
                select 1 where not exists (select 1 from assignment_lock where id = 1)
                """).executeUpdate();
    }

    private record Fixture(Ticket ticket, AppUser actor, AppUser otherAgent) { }
}
