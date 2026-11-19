package com.samsung.ees.infra.mock.core.exception;

/**
 * Thrown when a mock data generation request violates validation constraints.
 */
public class InvalidMockRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidMockRequestException(String message) {
        super(message);
    }
}
