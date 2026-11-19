package com.samsung.ees.infra.mock.core.exception;

/**
 * Thrown when a mock data generation request violates validation constraints.
 */
public class InvalidMockRequestException extends RuntimeException {

    public InvalidMockRequestException(String message) {
        super(message);
    }
}
