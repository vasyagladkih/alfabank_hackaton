package ru.alfahackaton.service.exception;

public class ApplicationNotFoundException extends RuntimeException {
    public ApplicationNotFoundException() { super("Заявка не найдена"); }
}
