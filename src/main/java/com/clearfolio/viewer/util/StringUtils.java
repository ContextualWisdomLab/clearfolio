package com.clearfolio.viewer.util;

/**
 * Utility class for string manipulation.
 */
public final class StringUtils {

    private StringUtils() {
        // Prevent instantiation
    }

    /**
     * Removes null characters from a string.
     *
     * @param value the string to clean
     * @return string without null characters
     */
    public static String removeNullChars(final String value) {
        if (value == null) {
            return null;
        }

        // ⚡ Bolt: Single-pass string sanitization
        // Avoids multiple allocations from chained replace() calls.
        int firstNull = value.indexOf('\u0000');
        if (firstNull < 0) {
            return value;
        }

        StringBuilder sb = new StringBuilder(value.length() - 1);
        sb.append(value, 0, firstNull);
        for (int i = firstNull + 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\u0000') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
