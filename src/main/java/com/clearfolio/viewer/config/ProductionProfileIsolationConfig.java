package com.clearfolio.viewer.config;

import java.util.Arrays;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Prevents production from composing the synthetic buyer-demo runtime profile.
 *
 * <p>The buyer-demo profile is intentionally a development and demonstration
 * surface. A production process must not inherit its fixture/configuration
 * authority merely because both profiles were supplied at startup.</p>
 */
@Configuration
@Profile("production")
public class ProductionProfileIsolationConfig {

    /**
     * Fails startup when production and buyer-demo profiles are active together.
     *
     * @param environment Spring environment containing the active profile authority
     */
    public ProductionProfileIsolationConfig(Environment environment) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("buyer-demo")) {
            throw new IllegalStateException(
                    "production profile cannot run with buyer-demo profile"
            );
        }
    }
}
