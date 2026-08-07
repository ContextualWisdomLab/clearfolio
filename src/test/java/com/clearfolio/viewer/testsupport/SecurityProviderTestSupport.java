package com.clearfolio.viewer.testsupport;

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides one shared synchronization boundary and provider snapshot for tests
 * that temporarily remove JVM-wide SHA-256 security providers.
 *
 * <p>The Java security-provider registry is global to the test JVM. Tests in
 * different packages must therefore use the same lock in addition to JUnit's
 * shared resource lock, otherwise parallel execution can observe an incomplete
 * provider list or restore providers in the wrong order.</p>
 */
public final class SecurityProviderTestSupport {

    /** Shared monitor used by every test that mutates the provider registry. */
    public static final Object SECURITY_PROVIDERS_LOCK = new Object();

    private SecurityProviderTestSupport() {
        // Utility class.
    }

    /**
     * Returns every installed provider that implements SHA-256 together with its
     * original one-based position in the JVM provider registry.
     *
     * @return ordered SHA-256 provider positions
     */
    public static List<ProviderPosition> sha256ProviderPositions() {
        Provider[] installedProviders = Security.getProviders();
        List<ProviderPosition> positions = new ArrayList<>();
        for (int index = 0; index < installedProviders.length; index++) {
            Provider provider = installedProviders[index];
            if (provider.getService("MessageDigest", "SHA-256") != null) {
                positions.add(new ProviderPosition(provider, index + 1));
            }
        }
        return positions;
    }

    /**
     * Stores one provider and its original one-based registry position.
     *
     * @param provider installed security provider
     * @param position original one-based registry position
     */
    public record ProviderPosition(Provider provider, int position) {
    }
}
