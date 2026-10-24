package com.ticketmanagement.backend.enums;

import java.util.Set;

public enum TicketStatus {
    IN_QUEUE,
    FULFILLMENT,
    RESOLVED,
    CANCELLED;

    /** Statuses still requiring work; RESOLVED and CANCELLED are final. */
    public static final Set<TicketStatus> UNRESOLVED = Set.of(IN_QUEUE, FULFILLMENT);
}
