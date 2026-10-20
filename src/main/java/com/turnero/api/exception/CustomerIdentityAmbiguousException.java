package com.turnero.api.exception;

public class CustomerIdentityAmbiguousException extends RuntimeException {
    public CustomerIdentityAmbiguousException() {
        super("Contact the business to arrange your appointment.");
    }
}
