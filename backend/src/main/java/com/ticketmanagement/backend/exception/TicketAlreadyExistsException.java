package com.ticketmanagement.backend.exception;

public class TicketAlreadyExistsException extends RuntimeException {
    public TicketAlreadyExistsException(String message) {
        super(message);
    }
}
