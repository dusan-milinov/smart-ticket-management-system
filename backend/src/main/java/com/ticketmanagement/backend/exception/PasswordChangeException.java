package com.ticketmanagement.backend.exception;

public class PasswordChangeException extends RuntimeException {

    public PasswordChangeException(String message) {
        super(message);
    }
}
