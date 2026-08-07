package com.clearfolio.viewer.artifact;

import org.springframework.http.HttpStatus;

/**
 * Token verification failure with the HTTP status the artifact endpoint should return.
 */
public class ArtifactTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** HTTP response status associated with this verified token failure category. */
    private final HttpStatus status;

    /**
     * Creates a token exception.
     *
     * @param status HTTP status to return
     * @param message failure message
     */
    public ArtifactTokenException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status for this token failure.
     *
     * @return HTTP status
     */
    public HttpStatus getStatus() {
        return status;
    }
}
