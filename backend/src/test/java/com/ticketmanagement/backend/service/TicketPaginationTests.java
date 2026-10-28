package com.ticketmanagement.backend.service;

import com.ticketmanagement.backend.dto.ticket.TicketResponse;
import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.enums.TicketStatus;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import com.ticketmanagement.backend.repository.TicketRepository;
import com.ticketmanagement.backend.repository.UserRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketPaginationTests {

    @Mock private TicketRepository tickets;
    @Mock private UserRepository users;
    @Mock private TicketEventRecorder events;
    @Mock private CurrentUserService currentUser;
    @Spy private TicketAccessPolicy policy = new TicketAccessPolicy();
    @InjectMocks private TicketService service;

    private final Pageable pageable = PageRequest.of(1, 2, Sort.by("ticketId"));

    enum Query {
        ALL, STATUS, CREATOR, ASSIGNEE, CREATOR_AND_STATUS, ASSIGNEE_AND_STATUS
    }

    @ParameterizedTest
    @EnumSource(Query.class)
    void mapsTicketsAndPreservesPageMetadata(Query query) {
        AppUser creator = new AppUser("creator@example.com", "password-hash", Role.USER);
        creator.setId(11L);
        AppUser assignee = new AppUser("agent@example.com", "password-hash", Role.SUPPORT_AGENT);
        assignee.setId(12L);
        Ticket ticket = new Ticket();
        ticket.setTicketId(42L);
        ticket.setCreator(creator);
        ticket.setAssignee(assignee);
        ticket.setTicketStatus(TicketStatus.FULFILLMENT);
        Page<Ticket> source = new PageImpl<>(List.of(ticket), pageable, 3);

        Page<TicketResponse> result = execute(query, source);

        assertThat(result.getContent()).containsExactly(TicketResponse.from(ticket));
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
        assertThat(result.isLast()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Query.class)
    void preservesEmptyAndOutOfRangePages(Query query) {
        Page<TicketResponse> empty = execute(query, Page.empty(pageable));

        assertThat(empty.getContent()).isEmpty();
        assertThat(empty.getNumber()).isEqualTo(1);
        assertThat(empty.getTotalElements()).isZero();
        assertThat(empty.getTotalPages()).isZero();

        Page<TicketResponse> outOfRange = execute(query, new PageImpl<>(List.of(), pageable, 1));

        assertThat(outOfRange.getContent()).isEmpty();
        assertThat(outOfRange.getTotalElements()).isEqualTo(1);
        assertThat(outOfRange.getTotalPages()).isEqualTo(1);
    }

    private Page<TicketResponse> execute(Query query, Page<Ticket> source) {
        if (query != Query.ALL && query != Query.STATUS) {
            when(currentUser.getCurrentUser()).thenReturn(new AppUser("admin@example.com", "hash", Role.ADMIN));
        }
        Page<TicketResponse> result = switch (query) {
            case ALL -> {
                when(tickets.findAll(pageable)).thenReturn(source);
                yield service.getAllTickets(pageable);
            }
            case STATUS -> {
                when(tickets.findByTicketStatus(TicketStatus.FULFILLMENT, pageable)).thenReturn(source);
                yield service.getTicketsByStatus(TicketStatus.FULFILLMENT, pageable);
            }
            case CREATOR -> {
                when(tickets.findByCreatorId(11L, pageable)).thenReturn(source);
                yield service.getTicketsByUserId(11L, pageable);
            }
            case ASSIGNEE -> {
                when(tickets.findByAssigneeId(12L, pageable)).thenReturn(source);
                yield service.getTicketsByAssigneeId(12L, pageable);
            }
            case CREATOR_AND_STATUS -> {
                when(tickets.findByCreatorIdAndTicketStatus(11L, TicketStatus.FULFILLMENT, pageable))
                        .thenReturn(source);
                yield service.getTicketsByUserIdAndStatus(11L, TicketStatus.FULFILLMENT, pageable);
            }
            case ASSIGNEE_AND_STATUS -> {
                when(tickets.findByAssigneeIdAndTicketStatus(12L, TicketStatus.FULFILLMENT, pageable))
                        .thenReturn(source);
                yield service.getTicketsByAssigneeIdAndStatus(12L, TicketStatus.FULFILLMENT, pageable);
            }
        };
        verifyNoMoreInteractions(tickets);
        verifyNoInteractions(users, events);
        return result;
    }
}
