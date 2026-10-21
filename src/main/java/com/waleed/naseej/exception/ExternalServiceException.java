package com.waleed.naseej.exception;

/**
 * Thrown when an outside service (Claude, Google Places, Moyasar...) fails or returns
 * something the server can't use. Mapped to 502 Bad Gateway.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }
}
