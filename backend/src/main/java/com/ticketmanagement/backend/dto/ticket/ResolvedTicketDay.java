package com.ticketmanagement.backend.dto.ticket;

import java.time.LocalDate;

public record ResolvedTicketDay(LocalDate date, long count) {
}
