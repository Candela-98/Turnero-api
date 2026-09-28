package com.turnero.api.exception;

public class PublicTokenUsedException extends RuntimeException{

    public PublicTokenUsedException(String message) {
        super(message);
    }
}
