package com.turnero.api.exception;

public class CustomerBookingBlockedException extends RuntimeException {
    public CustomerBookingBlockedException() {
        super("Contact the business to arrange your appointment.");
    }
}
