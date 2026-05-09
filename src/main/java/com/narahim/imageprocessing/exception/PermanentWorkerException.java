package com.narahim.imageprocessing.exception;

public class PermanentWorkerException extends RuntimeException {

    private final int statusCode;

    public PermanentWorkerException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
