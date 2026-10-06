package com.college.library.exception;

/**
 * Custom runtime exception thrown when an entity is not found in the database.
 * Demonstrates Exception Handling in Java.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
