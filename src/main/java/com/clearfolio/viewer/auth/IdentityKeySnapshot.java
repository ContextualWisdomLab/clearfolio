package com.clearfolio.viewer.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable provider-neutral authority for one bounded identity-provider key snapshot.
 *
 * <p>The snapshot contains only server-owned verifier profile identity, key identifiers,
 * cache lifetime, and a monotonically assigned generation fence. It does not parse
 * tokens, select algorithms, fetch provider metadata, or carry JWK bodies. Callers
 * must still perform cryptographic verification with trusted key material and compare
 * the current configured generation before accepting a token key identifier.</p>
 */
public final class IdentityKeySnapshot {

    private final String verifierProfileId;
    private final long generation;
    private final Instant fetchedAt;
    private final Instant expiresAt;
    private final Set<String> trustedKeyIds;

    /**
     * Creates one immutable key snapshot.
     *
     * @param verifierProfileId server-owned verifier profile identifier
     * @param generation positive key-set generation used to fence stale snapshots
     * @param fetchedAt inclusive instant at which this snapshot became current
     * @param expiresAt exclusive cache-expiration instant
     * @param trustedKeyIds non-empty exact key identifiers in this generation
     * @throws NullPointerException when a required value or key identifier is null
     * @throws IllegalArgumentException when an identifier is blank, generation is not
     *         positive, the lifetime is empty/reversed, or no trusted key is present
     */
    public IdentityKeySnapshot(
            String verifierProfileId,
            long generation,
            Instant fetchedAt,
            Instant expiresAt,
            Set<String> trustedKeyIds) {
        this.verifierProfileId = Objects.requireNonNull(verifierProfileId, "verifierProfileId");
        if (verifierProfileId.isBlank()) {
            throw new IllegalArgumentException("verifierProfileId must not be blank");
        }
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!fetchedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("fetchedAt must precede expiresAt");
        }
        Set<String> requiredKeyIds = Objects.requireNonNull(trustedKeyIds, "trustedKeyIds");
        if (requiredKeyIds.isEmpty()) {
            throw new IllegalArgumentException("trustedKeyIds must not be empty");
        }
        for (String keyId : requiredKeyIds) {
            Objects.requireNonNull(keyId, "trustedKeyIds must not contain null");
            if (keyId.isBlank()) {
                throw new IllegalArgumentException("trustedKeyIds must not contain blank identifiers");
            }
        }
        this.generation = generation;
        this.trustedKeyIds = Set.copyOf(requiredKeyIds);
    }

    /**
     * Returns the server-owned verifier profile identifier.
     *
     * @return verifier profile identifier
     */
    public String verifierProfileId() {
        return verifierProfileId;
    }

    /**
     * Returns the positive key-set generation fence.
     *
     * @return key-set generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Returns the inclusive instant at which this snapshot became current.
     *
     * @return fetch instant
     */
    public Instant fetchedAt() {
        return fetchedAt;
    }

    /**
     * Returns the exclusive snapshot cache-expiration instant.
     *
     * @return expiration instant
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * Returns an immutable copy of exact trusted key identifiers.
     *
     * @return immutable trusted key identifier set
     */
    public Set<String> trustedKeyIds() {
        return trustedKeyIds;
    }

    /**
     * Checks whether the snapshot is current at an exact verifier time.
     *
     * @param now verifier-owned current time
     * @return true from {@code fetchedAt} inclusive until {@code expiresAt} exclusive
     * @throws NullPointerException when {@code now} is null
     */
    public boolean isCurrentAt(Instant now) {
        Instant requiredNow = Objects.requireNonNull(now, "now");
        return !requiredNow.isBefore(fetchedAt) && requiredNow.isBefore(expiresAt);
    }

    /**
     * Checks whether this current snapshot authorizes an exact key identifier under
     * the caller's expected generation fence.
     *
     * @param keyId token key identifier to compare exactly
     * @param expectedGeneration caller-owned current generation
     * @param now verifier-owned current time
     * @return true only for a known key in the expected current snapshot generation
     * @throws NullPointerException when {@code keyId} is null
     */
    public boolean authorizes(String keyId, long expectedGeneration, Instant now) {
        String requiredKeyId = Objects.requireNonNull(keyId, "keyId");
        if (requiredKeyId.isBlank()
                || expectedGeneration != generation
                || !isCurrentAt(now)) {
            return false;
        }
        return trustedKeyIds.contains(requiredKeyId);
    }
}
