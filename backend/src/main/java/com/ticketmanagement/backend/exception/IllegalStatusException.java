package com.ticketmanagement.backend.exception;

public class IllegalStatusException extends RuntimeException {
    public IllegalStatusException(String message) {
        super(message);
    }
}
