package com.clearfolio.viewer.conversion;

/**
 * Typed failure returned by a qualified Office conversion adapter.
 *
 * <p>The stable failure code is the policy authority for retryability. Human-
 * readable messages remain diagnostic context and must not be parsed to decide
 * whether a conversion should be retried.</p>
 */
public final class OfficeConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final OfficeConversionFailureCode failureCode;

    /**
     * Creates a typed adapter failure.
     *
     * @param failureCode stable failure class
     * @param message non-empty diagnostic message
     * @throws IllegalArgumentException when the failure code or message is missing
     */
    public OfficeConversionException(
            OfficeConversionFailureCode failureCode,
            String message) {
        super(requireMessage(message));
        if (failureCode == null) {
            throw new IllegalArgumentException("failureCode must not be null");
        }
        this.failureCode = failureCode;
    }

    /**
     * Returns the stable conversion failure class.
     *
     * @return failure code used for retry and product error mapping
     */
    public OfficeConversionFailureCode failureCode() {
        return failureCode;
    }

    /**
     * Returns whether bounded retry is permitted for this failure class.
     *
     * @return {@code true} only for transient adapter/engine failures
     */
    public boolean isRetryable() {
        return failureCode.isRetryable();
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message.strip();
    }
}
