package com.example.reactor.exceptions;


public class EventAlreadyRegisteredException extends RuntimeException {
    public EventAlreadyRegisteredException(String s) {
        super(s);
    }
}
