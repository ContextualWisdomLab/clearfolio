package com.clearfolio.viewer.controller;

import java.util.Optional;

/**
 * Parses Clearfolio's deliberately narrow HTTP byte-range profile.
 *
 * <p>Clearfolio supports zero or one {@code bytes} range. Multiple ranges are
 * rejected rather than being interpreted inconsistently by different artifact
 * endpoints.</p>
 */
interface ArtifactHttpRange {

    String RANGE_UNIT_BYTES = "bytes";

    /**
     * Parses an optional single HTTP {@code bytes} range.
     *
     * @param rangeHeader optional Range header
     * @param totalLength current artifact byte length
     * @return empty for a full response, otherwise a parsed or rejected range
     */
    static Optional<ResolvedRange> resolveSingleRange(String rangeHeader, int totalLength) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rangeHeader.strip();
        if (!trimmed.startsWith(RANGE_UNIT_BYTES + "=")) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        String spec = trimmed.substring((RANGE_UNIT_BYTES + "=").length()).strip();
        if (spec.isEmpty()) {
            return Optional.of(ResolvedRange.invalidRange());
        }
        if (spec.contains(",")) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        int dash = spec.indexOf('-');
        if (dash < 0) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        String first = spec.substring(0, dash).strip();
        String second = spec.substring(dash + 1).strip();
        if (first.isEmpty()) {
            return resolveSuffix(second, totalLength);
        }
        return resolveStartEnd(first, second, totalLength);
    }

    private static Optional<ResolvedRange> resolveStartEnd(String first, String second, int totalLength) {
        long startLong;
        try {
            startLong = Long.parseLong(first);
        } catch (NumberFormatException ex) {
            return Optional.of(ResolvedRange.invalidRange());
        }
        if (startLong >= totalLength) {
            return Optional.of(ResolvedRange.unsatisfiableRange());
        }

        int start = (int) startLong;
        if (second.isEmpty()) {
            return Optional.of(ResolvedRange.ok(start, totalLength - 1));
        }

        long endLong;
        try {
            endLong = Long.parseLong(second);
        } catch (NumberFormatException ex) {
            return Optional.of(ResolvedRange.invalidRange());
        }
        if (endLong < startLong) {
            return Optional.of(ResolvedRange.unsatisfiableRange());
        }

        long boundedEnd = Math.min(endLong, totalLength - 1L);
        return Optional.of(ResolvedRange.ok(start, (int) boundedEnd));
    }

    private static Optional<ResolvedRange> resolveSuffix(String suffix, int totalLength) {
        if (suffix.isEmpty()) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        long suffixLong;
        try {
            suffixLong = Long.parseLong(suffix);
        } catch (NumberFormatException ex) {
            return Optional.of(ResolvedRange.invalidRange());
        }
        if (suffixLong <= 0L) {
            return Optional.of(ResolvedRange.invalidRange());
        }
        if (suffixLong >= totalLength) {
            return Optional.of(ResolvedRange.ok(0, totalLength - 1));
        }

        long startLong = totalLength - suffixLong;
        return Optional.of(ResolvedRange.ok((int) startLong, totalLength - 1));
    }

    /**
     * One resolved single-range outcome.
     *
     * @param startInclusive first byte when valid
     * @param endInclusive final byte when valid
     * @param invalid whether the syntax is outside Clearfolio's range profile
     * @param unsatisfiable whether the syntax is valid enough to parse but cannot be satisfied
     */
    record ResolvedRange(
            int startInclusive,
            int endInclusive,
            boolean invalid,
            boolean unsatisfiable
    ) {
        /**
         * Creates a valid resolved range.
         *
         * @param startInclusive first byte
         * @param endInclusive final byte
         * @return valid range
         */
        static ResolvedRange ok(int startInclusive, int endInclusive) {
            return new ResolvedRange(startInclusive, endInclusive, false, false);
        }

        /**
         * Creates an invalid-syntax result.
         *
         * @return invalid range result
         */
        static ResolvedRange invalidRange() {
            return new ResolvedRange(0, 0, true, false);
        }

        /**
         * Creates an unsatisfiable result.
         *
         * @return unsatisfiable range result
         */
        static ResolvedRange unsatisfiableRange() {
            return new ResolvedRange(0, 0, false, true);
        }
    }
}
