package com.ticketmanagement.backend.exception;

public class UserDeactivationException extends RuntimeException {
    public UserDeactivationException(String message) {
        super(message);
    }
}
