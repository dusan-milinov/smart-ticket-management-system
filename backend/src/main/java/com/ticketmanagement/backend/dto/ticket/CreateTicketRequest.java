package com.ticketmanagement.backend.dto.ticket;

import com.ticketmanagement.backend.enums.TicketPriority;
import com.ticketmanagement.backend.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTicketRequest(

        @NotBlank(message = "Ticket title is required")
        String title,

        @NotBlank(message = "Ticket description is required")
        String description,

        @NotNull(message = "Ticket priority is required")
        TicketPriority ticketPriority,

        @NotNull(message = "Creator ID is required")
        Long creatorId,

        @NotNull(message = "Ticket type is required")
        TicketType ticketType

) {
}
