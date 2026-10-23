package com.waleed.naseej.exception;

/**
 * Thrown when the user is known but isn't allowed to do or see something,
 * for example an investor opening a report they haven't unlocked. Mapped to 403 Forbidden.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}