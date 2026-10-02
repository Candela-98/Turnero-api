package com.turnero.api.exception;

public class InvalidPublicTokenException extends RuntimeException{

    public InvalidPublicTokenException(String message) {
        super(message);
    }
}
