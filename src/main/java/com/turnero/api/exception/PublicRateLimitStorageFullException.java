package com.turnero.api.exception;

/** Registry saturation is an availability failure, not a client's exhausted quota. */
public class PublicRateLimitStorageFullException extends RuntimeException {
    public PublicRateLimitStorageFullException() {
        super("Public booking is temporarily unavailable. Please try again later.");
    }
}
