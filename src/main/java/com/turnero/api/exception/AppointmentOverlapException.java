package com.turnero.api.exception;

public class AppointmentOverlapException extends RuntimeException{

    public AppointmentOverlapException(String message) {
        super(message);
    }
}
