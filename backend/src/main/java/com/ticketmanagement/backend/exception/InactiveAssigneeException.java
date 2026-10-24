package com.ticketmanagement.backend.exception;

/** Tickets cannot be assigned to deactivated accounts; mapped to 409. */
public class InactiveAssigneeException extends RuntimeException {
    public InactiveAssigneeException(Long userId) {
        super("User with ID " + userId + " is inactive and cannot be assigned tickets.");
    }
}
