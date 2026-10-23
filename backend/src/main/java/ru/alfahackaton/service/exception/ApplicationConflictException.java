package ru.alfahackaton.service.exception;

public class ApplicationConflictException extends RuntimeException {
    public ApplicationConflictException(String message) { super(message); }
}
