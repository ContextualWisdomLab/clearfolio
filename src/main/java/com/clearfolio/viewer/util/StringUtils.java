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
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\u0000') {
                if (sb == null) {
                    sb = new StringBuilder(value.length());
                    sb.append(value, 0, i);
                }
                continue; // 100% coverage by avoiding the missing else branch
            }

            if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? value : sb.toString();
    }
}
