package com.turnero.api.exception;

public class PublicTokenExpiredException extends RuntimeException{

    public PublicTokenExpiredException(String message) {
        super(message);
    }
}
