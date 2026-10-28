package com.ticketmanagement.backend.dto.ticket;

import com.ticketmanagement.backend.enums.Role;
import com.ticketmanagement.backend.model.AppUser;
import com.ticketmanagement.backend.model.Ticket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketResponseTests {

    @Test
    void includesTheCreatorEmail() {
        TicketResponse response = TicketResponse.from(ticket());

        assertThat(response.creatorId()).isEqualTo(11L);
        assertThat(response.creatorEmail()).isEqualTo("user@example.com");
    }

    @Test
    void includesTheCurrentAssigneeEmail() {
        Ticket ticket = ticket();
        AppUser assignee = new AppUser("agent@example.com", "password-hash", Role.SUPPORT_AGENT);
        assignee.setId(12L);
        ticket.setAssignee(assignee);

        TicketResponse response = TicketResponse.from(ticket);

        assertThat(response.assigneeId()).isEqualTo(12L);
        assertThat(response.assigneeEmail()).isEqualTo("agent@example.com");

        AppUser replacement = new AppUser("replacement@example.com", "password-hash", Role.SUPPORT_AGENT);
        replacement.setId(13L);
        ticket.setAssignee(replacement);

        TicketResponse reassigned = TicketResponse.from(ticket);

        assertThat(reassigned.assigneeId()).isEqualTo(13L);
        assertThat(reassigned.assigneeEmail()).isEqualTo("replacement@example.com");
    }

    @Test
    void leavesAssigneeFieldsNullForUnassignedTickets() {
        TicketResponse response = TicketResponse.from(ticket());

        assertThat(response.assigneeId()).isNull();
        assertThat(response.assigneeEmail()).isNull();
    }

    private Ticket ticket() {
        AppUser creator = new AppUser("user@example.com", "password-hash", Role.USER);
        creator.setId(11L);
        Ticket ticket = new Ticket();
        ticket.setCreator(creator);
        return ticket;
    }
}
