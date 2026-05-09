package com.narahim.imageprocessing.exception;

public class RetryableConflictException extends RuntimeException {

    public RetryableConflictException() {
        super("Job creation is in progress. Please retry with the same Idempotency-Key.");
    }
}
