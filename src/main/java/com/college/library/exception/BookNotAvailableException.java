package com.college.library.exception;

/**
 * Custom runtime exception thrown when attempting to issue an unavailable book.
 */
public class BookNotAvailableException extends RuntimeException {
    public BookNotAvailableException(String message) {
        super(message);
    }
}
