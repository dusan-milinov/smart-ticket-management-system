package com.ticketmanagement.backend.seeder;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.enums.TicketType;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Comment;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.model.TicketEvent;
import com.ticketmanagement.backend.repository.CommentRepository;
import com.ticketmanagement.backend.repository.TicketEventRepository;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "app.seed.enabled=false")
@Import(TicketSeedingTests.SeedTestConfiguration.class)
class TicketSeedingTests {

    @Autowired private TicketSeeder seeder;
    @Autowired private TicketFixtureSeeder fixtureSeeder;
    @Autowired private CommentSeeder commentSeeder;
    @Autowired private TicketRepository tickets;
    @Autowired private UserRepository users;
    @Autowired private CommentRepository comments;
    @Autowired private TicketEventRepository history;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private TicketEventSeeder eventSeeder;

    @Test
    void seedsExactFixturesWithConsistentHistoryAndRelationships() {
        inRollbackTransaction(status -> {
            prepareFixtures();
            seeder.validateTypes();
            seeder.seed();
            entityManager.flush();
            entityManager.clear();

            List<Ticket> fixtures = fixtureTickets();
            assertThat(fixtures).hasSize(28);
            assertThat(fixtures).extracting(Ticket::getTicketNumber).containsExactlyElementsOf(
                    IntStream.rangeClosed(1, 28).mapToObj(number -> "TKT-SEED-%04d".formatted(number)).toList());
            assertThat(fixtures).extracting(Ticket::getTicketType)
                    .containsExactlyElementsOf(TicketSeeder.FIXTURES.stream().map(TicketSeeder.Fixture::type).toList());
            assertThat(fixtures).extracting(Ticket::getTicketPriority)
                    .containsExactlyElementsOf(TicketSeeder.FIXTURES.stream().map(TicketSeeder.Fixture::priority).toList());
            assertThat(fixtures.subList(0, 8)).extracting(Ticket::getTicketStatus).containsExactly(
                    TicketStatus.RESOLVED, TicketStatus.RESOLVED,
                    TicketStatus.FULFILLMENT, TicketStatus.FULFILLMENT, TicketStatus.FULFILLMENT,
                    TicketStatus.FULFILLMENT, TicketStatus.FULFILLMENT, TicketStatus.CANCELLED);
            assertThat(fixtures.stream().mapToInt(ticket -> comments.findAllByTicket(ticket).size()).sum())
                    .isEqualTo(35);
            assertThat(fixtures.stream().map(ticket -> comments.findAllByTicket(ticket).size()).toList())
                    .containsExactly(3, 2, 1, 1, 1, 0, 0, 0,
                            3, 1, 0, 2, 2, 1, 1, 2, 0, 2, 2, 0, 3, 1, 2, 0, 1, 1, 0, 3);
            List<Ticket> added = fixtures.subList(8, 28);
            assertThat(added).extracting(Ticket::getTicketType).containsOnly(TicketType.values());
            assertThat(added).extracting(Ticket::getTicketPriority).containsOnly(TicketPriority.values());
            assertThat(added.stream().collect(Collectors.groupingBy(Ticket::getTicketStatus, Collectors.counting())))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            TicketStatus.FULFILLMENT, 9L, TicketStatus.RESOLVED, 8L, TicketStatus.CANCELLED, 3L));
            assertThat(added).extracting(ticket -> ticket.getCreator().getEmail())
                    .containsOnly(TicketSeeder.PETAR, TicketSeeder.VOJIN, TicketSeeder.VESNA, TicketSeeder.SARAH);
            assertThat(added).extracting(ticket -> ticket.getAssignee().getEmail())
                    .containsOnly(TicketSeeder.ZIKA, TicketSeeder.NIKOLA, TicketSeeder.THIRD_AGENT,
                            TicketSeeder.ANDJELA, TicketSeeder.EMILY);
            assertThat(comments.findAllByTicket(fixtures.get(0)))
                    .extracting(comment -> comment.getAuthor().getRole())
                    .containsExactlyInAnyOrder(Role.SUPPORT_AGENT, Role.USER, Role.SUPPORT_AGENT);
            assertThat(comments.findAllByTicket(fixtures.get(1)))
                    .extracting(comment -> comment.getAuthor().getRole())
                    .containsExactlyInAnyOrder(Role.SUPPORT_AGENT, Role.ADMIN);
            assertThat(fixtures.stream().filter(ticket -> history.findAllByTicket(ticket).stream()
                            .anyMatch(event -> event.getEventType() == TicketEventType.REASSIGNED))
                    .map(Ticket::getTicketNumber).toList()).containsExactly(
                            "TKT-SEED-0002", "TKT-SEED-0004", "TKT-SEED-0009", "TKT-SEED-0012",
                            "TKT-SEED-0016", "TKT-SEED-0019", "TKT-SEED-0021", "TKT-SEED-0025", "TKT-SEED-0028");
            assertThat(fixtures.stream().mapToInt(ticket -> history.findAllByTicket(ticket).size()).sum())
                    .isEqualTo(142);
            for (int index = 0; index < fixtures.size(); index++) {
                verifyFixture(fixtures.get(index), TicketSeeder.FIXTURES.get(index));
            }
        });
    }

    @Test
    void repeatRunsPreserveAllRowsAndExistingEdits() {
        inRollbackTransaction(status -> {
            prepareFixtures();
            seeder.seed();
            List<Ticket> fixtures = fixtureTickets();
            Ticket first = fixtures.getFirst();
            first.setTitle("Locally edited fixture title");
            first.setResolutionSummary("A locally maintained summary");
            Comment editedComment = comments.findAllByTicket(first).getFirst();
            editedComment.setContent("Existing comment must not be replaced");
            entityManager.flush();
            List<String> before = snapshot();

            seeder.seed();
            entityManager.flush();
            entityManager.clear();

            assertThat(snapshot()).isEqualTo(before);
        });
    }

    @Test
    void extendsOriginalEightFixturesWithoutChangingTheirRows() {
        inRollbackTransaction(status -> {
            prepareFixtures();
            TicketSeeder.FIXTURES.subList(0, 8).forEach(fixtureSeeder::seed);
            entityManager.flush();
            List<String> originalRows = snapshot();

            seeder.seed();
            entityManager.flush();
            entityManager.clear();

            assertThat(snapshot()).hasSize(28).startsWith(originalRows.toArray(String[]::new));
            List<String> extendedRows = snapshot();
            seeder.seed();
            entityManager.flush();
            entityManager.clear();
            assertThat(snapshot()).isEqualTo(extendedRows);
        });
    }

    @Test
    void partiallyPopulatedDataOnlyCreatesMissingFixturesAndDoesNotRepairChildren() {
        inRollbackTransaction(status -> {
            prepareFixtures();
            fixtureSeeder.seed(TicketSeeder.FIXTURES.get(0));
            Ticket first = fixtureTickets().getFirst();
            entityManager.createQuery("delete from Comment c where c.ticket = :ticket")
                    .setParameter("ticket", first).executeUpdate();
            entityManager.createQuery("delete from TicketEvent e where e.ticket = :ticket")
                    .setParameter("ticket", first).executeUpdate();
            Long id = first.getTicketId();

            seeder.seed();
            entityManager.flush();
            entityManager.clear();

            List<Ticket> fixtures = fixtureTickets();
            assertThat(fixtures).hasSize(28);
            assertThat(fixtures.getFirst().getTicketId()).isEqualTo(id);
            assertThat(comments.findAllByTicket(fixtures.getFirst())).isEmpty();
            assertThat(history.findAllByTicket(fixtures.getFirst())).isEmpty();
            assertThat(fixtures.stream().mapToInt(ticket -> comments.findAllByTicket(ticket).size()).sum())
                    .isEqualTo(32);
            assertThat(fixtures.stream().mapToInt(ticket -> history.findAllByTicket(ticket).size()).sum())
                    .isEqualTo(135);
        });
    }

    @Test
    void commentFromAnUnassignedAgentIsRejectedWithoutAddingRows() {
        inRollbackTransaction(status -> {
            prepareFixtures();
            fixtureSeeder.seed(TicketSeeder.FIXTURES.getFirst());
            Ticket ticket = fixtureTickets().getFirst();
            AppUser unassignedAgent = users.findByEmail(TicketSeeder.NIKOLA).orElseThrow();
            List<String> before = snapshot();

            assertThatThrownBy(() -> commentSeeder.seed(ticket, unassignedAgent,
                    "Not an assigned agent's comment", ticket.getUpdatedAt().plusSeconds(60)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invalid seed comment author for TKT-SEED-0001: " + TicketSeeder.NIKOLA);

            assertThat(snapshot()).isEqualTo(before);
        });
    }

    @Test
    void eventConstructorsSupportExplicitAndDefaultTimestamps() {
        Instant timestamp = Instant.parse("2026-01-01T10:00:00Z");
        assertThat(new TicketEvent(timestamp).getCreatedAt()).isEqualTo(timestamp);
        assertThat(new TicketEvent().getCreatedAt()).isNotNull();
        assertThatThrownBy(() -> new TicketEvent((Instant) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Event timestamp is required");
    }

    @Test
    void missingRequiredUserFailsClearly() {
        inRollbackTransaction(status -> {
            prepareFixtures();
            AppUser creator = users.findByEmail(TicketSeeder.PETAR).orElseThrow();
            creator.setEmail("temporarily-hidden-seed-creator@example.com");
            entityManager.flush();

            assertThatThrownBy(() -> fixtureSeeder.seed(TicketSeeder.FIXTURES.getFirst()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Required seed user is unavailable: " + TicketSeeder.PETAR);
            assertThat(fixtureTickets()).isEmpty();
        });
    }

    @Test
    void existingUserWithWrongRoleFailsWithoutBeingOverwritten() {
        inRollbackTransaction(status -> {
            prepareFixtures();
            AppUser agent = users.findByEmail(TicketSeeder.ZIKA).orElseThrow();
            agent.setRole(Role.USER);
            entityManager.flush();

            assertThatThrownBy(() -> fixtureSeeder.seed(TicketSeeder.FIXTURES.getFirst()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Seed user " + TicketSeeder.ZIKA + " must have role SUPPORT_AGENT but has USER");
            assertThat(agent.getRole()).isEqualTo(Role.USER);
            assertThat(fixtureTickets()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {TicketSeeder.ZIKA, TicketSeeder.ANDJELA})
    void inactiveInitialOrReassignedAgentFailsClearlyWithoutReactivation(String agentEmail) {
        inRollbackTransaction(status -> {
            prepareFixtures();
            AppUser agent = users.findByEmail(agentEmail).orElseThrow();
            agent.deactivate();
            entityManager.flush();

            assertThatThrownBy(() -> fixtureSeeder.seed(TicketSeeder.FIXTURES.get(8)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Seed agent " + agentEmail + " is inactive and cannot be assigned tickets");
            assertThat(agent.isActive()).isFalse();
            assertThat(fixtureTickets()).isEmpty();
        });
    }

    @Test
    void lateHistoryFailureRollsBackActualTicketCommentAndEventWrites() {
        List<String> before = new TransactionTemplate(transactionManager).execute(status -> snapshot());
        AtomicBoolean writesObserved = new AtomicBoolean();
        TicketEventSeeder eventTarget = AopTestUtils.getUltimateTargetObject(eventSeeder);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            Ticket ticket = invocation.getArgument(0);
            assertThat(tickets.existsByTicketNumber(ticket.getTicketNumber())).isTrue();
            assertThat(comments.findAllByTicket(ticket)).hasSize(3);
            assertThat(history.findAllByTicket(ticket)).hasSize(8);
            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.RESOLVED);
            writesObserved.set(true);
            throw new IllegalStateException("Injected late fixture history failure");
        }).when(eventTarget).record(any(Ticket.class), any(AppUser.class),
                eq(TicketEventType.RESOLVED), isNull(), any(String.class), any(Instant.class));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            prepareFixtures();
            fixtureSeeder.seed(TicketSeeder.FIXTURES.get(8));
        })).isInstanceOf(IllegalStateException.class).hasMessage("Injected late fixture history failure");

        assertThat(writesObserved).isTrue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(snapshot()).isEqualTo(before));
    }

    @Test
    void childSeedersRejectCallsWithoutATransaction() {
        assertThatThrownBy(() -> commentSeeder.seed(new Ticket(), new AppUser("unused", "unused", Role.USER),
                "unused", Instant.now())).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> eventSeeder.record(new Ticket(), new AppUser("unused", "unused", Role.USER),
                TicketEventType.CREATED, null, null, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private void verifyFixture(Ticket ticket, TicketSeeder.Fixture fixture) {
        assertThat(ticket.getTicketId()).isPositive();
        assertThat(ticket.getTitle()).isEqualTo(fixture.title());
        assertThat(ticket.getDescription()).isNotBlank();
        assertThat(ticket.getCreator().getEmail()).isEqualTo(fixture.creatorEmail());
        assertThat(ticket.getCreator().getRole()).isEqualTo(Role.USER);
        assertThat(ticket.getAssignee().getEmail()).isEqualTo(
                fixture.reassignedAgentEmail() == null ? fixture.initialAgentEmail() : fixture.reassignedAgentEmail());
        assertThat(ticket.getAssignee().getRole()).isEqualTo(Role.SUPPORT_AGENT);
        assertThat(ticket.getAssignee().isActive()).isTrue();
        assertThat(ticket.getCreatedAt()).isEqualTo(
                Instant.parse("2026-01-01T09:00:00Z").plusSeconds(fixture.number() * 86400L));
        assertThat(ticket.getTicketStatus()).isEqualTo(fixture.resolution() != null ? TicketStatus.RESOLVED
                : fixture.cancelled() ? TicketStatus.CANCELLED : TicketStatus.FULFILLMENT);
        assertThat(ticket.getResolutionSummary()).isEqualTo(fixture.resolution());
        List<Comment> ticketComments = comments.findAllByTicket(ticket).stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt)).toList();
        assertThat(ticketComments).hasSize(fixture.comments().size());
        for (int index = 0; index < ticketComments.size(); index++) {
            Comment comment = ticketComments.get(index);
            assertThat(comment.getId()).isPositive();
            assertThat(comment.getTicket().getTicketId()).isEqualTo(ticket.getTicketId());
            assertThat(comment.getAuthor().getEmail()).isEqualTo(fixture.comments().get(index).authorEmail());
            assertThat(comment.getContent()).isEqualTo(fixture.comments().get(index).content());
        }
        List<TicketEvent> events = history.findAllByTicket(ticket).stream()
                .sorted(Comparator.comparing(TicketEvent::getCreatedAt)).toList();
        List<TicketEventType> expectedEvents = new ArrayList<>(List.of(
                TicketEventType.CREATED, TicketEventType.ASSIGNED, TicketEventType.STATUS_CHANGED));
        if (fixture.reassignedAgentEmail() != null) {
            expectedEvents.add(TicketEventType.REASSIGNED);
        }
        fixture.comments().forEach(comment -> expectedEvents.add(TicketEventType.COMMENT_ADDED));
        if (fixture.resolution() != null) {
            expectedEvents.add(TicketEventType.RESOLVED);
        } else if (fixture.cancelled()) {
            expectedEvents.add(TicketEventType.STATUS_CHANGED);
        }
        assertThat(events).extracting(TicketEvent::getEventType).containsExactlyElementsOf(expectedEvents);
        assertThat(events.getFirst().getEventType()).isEqualTo(TicketEventType.CREATED);
        assertThat(events.getFirst().getInitiator().getId()).isEqualTo(ticket.getCreator().getId());
        assertThat(events.getFirst().getCreatedAt()).isEqualTo(ticket.getCreatedAt());
        assertThat(events.getLast().getCreatedAt()).isEqualTo(ticket.getUpdatedAt());
        assertThat(events.stream().filter(event -> event.getEventType() == TicketEventType.REASSIGNED).count())
                .isEqualTo(fixture.reassignedAgentEmail() == null ? 0 : 1);
        TicketStatus status = TicketStatus.IN_QUEUE;
        String assigneeId = null;
        Instant previous = ticket.getCreatedAt().minusSeconds(1);
        int commentIndex = 0;
        for (TicketEvent event : events) {
            assertThat(event.getId()).isPositive();
            assertThat(event.getCreatedAt()).isAfter(previous);
            assertThat(event.getTicket().getTicketId()).isEqualTo(ticket.getTicketId());
            previous = event.getCreatedAt();
            switch (event.getEventType()) {
                case CREATED -> {
                    assertThat(event.getOldValue()).isNull();
                    assertThat(event.getNewValue()).isNull();
                }
                case ASSIGNED, REASSIGNED -> {
                    assertThat(event.getInitiator().getRole()).isEqualTo(Role.ADMIN);
                    assertThat(event.getOldValue()).isEqualTo(assigneeId);
                    assertThat(event.getNewValue()).isNotEqualTo(assigneeId);
                    assigneeId = event.getNewValue();
                    AppUser assignedAgent = users.findById(Long.valueOf(assigneeId)).orElseThrow();
                    assertThat(assignedAgent.getRole()).isEqualTo(Role.SUPPORT_AGENT);
                    assertThat(assignedAgent.isActive()).isTrue();
                    assertThat(assignedAgent.getEmail()).isEqualTo(event.getEventType() == TicketEventType.ASSIGNED
                            ? fixture.initialAgentEmail() : fixture.reassignedAgentEmail());
                }
                case STATUS_CHANGED -> {
                    assertThat(event.getInitiator().getRole()).isEqualTo(Role.ADMIN);
                    assertThat(event.getOldValue()).isEqualTo(status.name());
                    status = TicketStatus.valueOf(event.getNewValue());
                    assertThat(status).isNotEqualTo(TicketStatus.RESOLVED);
                }
                case COMMENT_ADDED -> {
                    Comment comment = ticketComments.get(commentIndex++);
                    assertThat(status).isEqualTo(TicketStatus.FULFILLMENT);
                    assertThat(event.getInitiator().getId()).isEqualTo(comment.getAuthor().getId());
                    assertThat(event.getCreatedAt()).isEqualTo(comment.getCreatedAt());
                    assertThat(event.getOldValue()).isNull();
                    assertThat(event.getNewValue()).isNull();
                    AppUser author = comment.getAuthor();
                    assertThat(author.getRole() == Role.ADMIN
                            || author.getId().equals(ticket.getCreator().getId())
                            || author.getId().toString().equals(assigneeId)).isTrue();
                }
                case RESOLVED -> {
                    assertThat(status).isEqualTo(TicketStatus.FULFILLMENT);
                    assertThat(event.getInitiator().getId().toString()).isEqualTo(assigneeId);
                    assertThat(event.getOldValue()).isNull();
                    assertThat(event.getNewValue()).isEqualTo(ticket.getResolutionSummary()).isNotBlank();
                    status = TicketStatus.RESOLVED;
                }
                default -> throw new AssertionError("Unexpected fixture event: " + event.getEventType());
            }
        }
        assertThat(commentIndex).isEqualTo(ticketComments.size());
        assertThat(status).isEqualTo(ticket.getTicketStatus());
        assertThat(assigneeId).isEqualTo(ticket.getAssignee().getId().toString());
    }

    private void inRollbackTransaction(Consumer<org.springframework.transaction.TransactionStatus> assertions) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            assertions.accept(status);
        });
    }

    private void prepareFixtures() {
        List<String> numbers = TicketSeeder.FIXTURES.stream().map(TicketSeeder.Fixture::ticketNumber).toList();
        // These changes are confined to rollback-only test transactions, restoring any existing fixtures.
        entityManager.createQuery("delete from Comment c where c.ticket.ticketNumber in :numbers")
                .setParameter("numbers", numbers).executeUpdate();
        entityManager.createQuery("delete from TicketEvent e where e.ticket.ticketNumber in :numbers")
                .setParameter("numbers", numbers).executeUpdate();
        entityManager.createQuery("delete from Ticket t where t.ticketNumber in :numbers")
                .setParameter("numbers", numbers).executeUpdate();
        Map<String, Role> required = Map.of(TicketSeeder.ADMIN, Role.ADMIN,
                TicketSeeder.PETAR, Role.USER, TicketSeeder.VOJIN, Role.USER,
                TicketSeeder.ZIKA, Role.SUPPORT_AGENT, TicketSeeder.NIKOLA, Role.SUPPORT_AGENT,
                TicketSeeder.THIRD_AGENT, Role.SUPPORT_AGENT,
                TicketSeeder.ANDJELA, Role.SUPPORT_AGENT, TicketSeeder.EMILY, Role.SUPPORT_AGENT,
                TicketSeeder.VESNA, Role.USER, TicketSeeder.SARAH, Role.USER);
        required.forEach((email, role) -> {
            AppUser user = users.findByEmail(email)
                    .orElseGet(() -> users.save(new AppUser(email, "test-only-unused-hash", role)));
            user.setRole(role);
        });
        entityManager.flush();
        // AppUser exposes no reactivation, so the rollback-only transaction resets the flag in bulk.
        entityManager.createQuery("update AppUser u set u.active = true where u.email in :emails")
                .setParameter("emails", required.keySet()).executeUpdate();
        entityManager.clear();
    }

    private List<Ticket> fixtureTickets() {
        return entityManager.createQuery("select t from Ticket t where t.ticketNumber in :numbers order by t.ticketNumber",
                        Ticket.class)
                .setParameter("numbers", TicketSeeder.FIXTURES.stream().map(TicketSeeder.Fixture::ticketNumber).toList())
                .getResultList();
    }

    private List<String> snapshot() {
        return fixtureTickets().stream().map(ticket -> {
            String commentRows = comments.findAllByTicket(ticket).stream()
                    .sorted(Comparator.comparing(Comment::getId))
                    .map(comment -> comment.getId() + ":" + comment.getAuthor().getId() + ":"
                            + comment.getContent() + ":" + comment.getCreatedAt())
                    .collect(Collectors.joining("|"));
            String eventRows = history.findAllByTicket(ticket).stream()
                    .sorted(Comparator.comparing(TicketEvent::getId))
                    .map(event -> event.getId() + ":" + event.getEventType() + ":" + event.getInitiator().getId()
                            + ":" + event.getOldValue() + ":" + event.getNewValue() + ":" + event.getCreatedAt())
                    .collect(Collectors.joining("|"));
            return ticket.getTicketId() + ":" + ticket.getTicketNumber() + ":" + ticket.getTitle()
                    + ":" + ticket.getDescription() + ":" + ticket.getTicketType() + ":" + ticket.getTicketPriority()
                    + ":" + ticket.getTicketStatus() + ":" + ticket.getResolutionSummary()
                    + ":" + ticket.getCreator().getId()
                    + ":" + (ticket.getAssignee() == null ? null : ticket.getAssignee().getId())
                    + ":" + ticket.getCreatedAt() + ":" + ticket.getUpdatedAt()
                    + ":" + commentRows + ":" + eventRows;
        }).toList();
    }

    @TestConfiguration
    @Import({TicketSeeder.class, TicketFixtureSeeder.class, CommentSeeder.class, TicketEventSeeder.class})
    static class SeedTestConfiguration {
    }
}
