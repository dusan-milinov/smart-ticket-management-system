package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketEventType;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.exception.IllegalStatusException;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketStartProcessingTests {

    @Mock private TicketRepository tickets;
    @Mock private UserRepository users;
    @Mock private TicketEventRecorder events;
    @Mock private TicketAssignmentService assignments;
    @Mock private CurrentUserService currentUser;
    @Spy private TicketAccessPolicy policy = new TicketAccessPolicy();
    @InjectMocks private TicketService service;

    @Test
    void movesAssignedQueuedTicketToFulfillmentAndRecordsStatusChange() {
        Ticket ticket = ticket(TicketStatus.IN_QUEUE);
        when(tickets.findByTicketId(42L)).thenReturn(Optional.of(ticket));

        when(currentUser.getCurrentUser()).thenReturn(ticket.getAssignee());
        var result = service.startTicketProcessing(42L);

        assertThat(result.ticketStatus()).isEqualTo(TicketStatus.FULFILLMENT);
        verify(events).recordEvent(42L, 12L, TicketEventType.STATUS_CHANGED,
                TicketStatus.IN_QUEUE.name(), TicketStatus.FULFILLMENT.name());
    }

    @Test
    void doesNotRepeatStatusEventsForTicketAlreadyInFulfillment() {
        Ticket ticket = ticket(TicketStatus.FULFILLMENT);
        when(tickets.findByTicketId(42L)).thenReturn(Optional.of(ticket));

        when(currentUser.getCurrentUser()).thenReturn(ticket.getAssignee());
        var result = service.startTicketProcessing(42L);

        assertThat(result.ticketStatus()).isEqualTo(TicketStatus.FULFILLMENT);
        verifyNoInteractions(events);
    }

    @Test
    void rejectsStartingTicketsAssignedToAnotherAgent() {
        Ticket ticket = ticket(TicketStatus.IN_QUEUE);
        when(tickets.findByTicketId(42L)).thenReturn(Optional.of(ticket));

        AppUser otherAgent = new AppUser("other@example.com", "password-hash", Role.SUPPORT_AGENT);
        otherAgent.setId(99L);
        when(currentUser.getCurrentUser()).thenReturn(otherAgent);
        assertThatThrownBy(() -> service.startTicketProcessing(42L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(events);
    }

    @Test
    void rejectsStartingResolvedOrCancelledTickets() {
        Ticket ticket = ticket(TicketStatus.RESOLVED);
        when(tickets.findByTicketId(42L)).thenReturn(Optional.of(ticket));

        when(currentUser.getCurrentUser()).thenReturn(ticket.getAssignee());
        assertThatThrownBy(() -> service.startTicketProcessing(42L))
                .isInstanceOf(IllegalStatusException.class);
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"IN_QUEUE", "FULFILLMENT"})
    void reassignmentLeavesTicketPendingAndRecordsAnyStatusChange(TicketStatus status) {
        Ticket ticket = ticket(status);
        AppUser replacement = new AppUser("replacement@example.com", "password-hash", Role.SUPPORT_AGENT);
        replacement.setId(13L);
        when(tickets.findByTicketId(42L)).thenReturn(Optional.of(ticket));
        when(users.findByIdForUpdate(13L)).thenReturn(Optional.of(replacement));
        AppUser admin = new AppUser("admin@example.com", "password-hash", Role.ADMIN);
        admin.setId(99L);
        when(currentUser.getCurrentUser()).thenReturn(admin);

        var result = service.reassign(42L, 13L);

        assertThat(result.ticketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
        assertThat(result.assigneeId()).isEqualTo(13L);
        assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.IN_QUEUE);
        verify(events).recordEvent(42L, 99L, TicketEventType.REASSIGNED, "12", "13");
        if (status == TicketStatus.FULFILLMENT) {
            verify(events).recordEvent(42L, 99L, TicketEventType.STATUS_CHANGED,
                    TicketStatus.FULFILLMENT.name(), TicketStatus.IN_QUEUE.name());
        } else {
            verify(events, never()).recordEvent(anyLong(), anyLong(), eq(TicketEventType.STATUS_CHANGED),
                    anyString(), anyString());
        }
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"RESOLVED", "CANCELLED"})
    void rejectsReassigningFinishedTickets(TicketStatus status) {
        Ticket ticket = ticket(status);
        AppUser replacement = new AppUser("replacement@example.com", "password-hash", Role.SUPPORT_AGENT);
        replacement.setId(13L);
        when(tickets.findByTicketId(42L)).thenReturn(Optional.of(ticket));
        when(users.findByIdForUpdate(13L)).thenReturn(Optional.of(replacement));
        AppUser admin = new AppUser("admin@example.com", "password-hash", Role.ADMIN);
        admin.setId(99L);
        when(currentUser.getCurrentUser()).thenReturn(admin);

        assertThatThrownBy(() -> service.reassign(42L, 13L))
                .isInstanceOf(IllegalStatusException.class);
        assertThat(ticket.getTicketStatus()).isEqualTo(status);
        assertThat(ticket.getAssignee().getId()).isEqualTo(12L);
        verifyNoInteractions(events);
    }

    private Ticket ticket(TicketStatus status) {
        AppUser creator = new AppUser("user@example.com", "password-hash", Role.USER);
        creator.setId(11L);
        AppUser assignee = new AppUser("agent@example.com", "password-hash", Role.SUPPORT_AGENT);
        assignee.setId(12L);

        Ticket ticket = new Ticket();
        ticket.setTicketId(42L);
        ticket.setCreator(creator);
        ticket.setAssignee(assignee);
        ticket.setTicketStatus(status);
        return ticket;
    }
}
