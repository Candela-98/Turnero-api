package com.turnero.api.exception;

public class PublicRateLimitExceededException extends RuntimeException{

    private final long retryAfterSeconds;

    public PublicRateLimitExceededException(long retryAfterSeconds) {
        super("Too many requests. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
