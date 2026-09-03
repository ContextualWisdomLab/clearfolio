#!/bin/bash

# Update TenantClaimsFuzzTest.java to use a lambda for CredentialRegistryPort
sed -i 's/name -> com.clearfolio.viewer.security.CredentialRegistryPort.TENANT_CLAIMS_HMAC_SECRET.equals(name)\n                            ? java.util.Optional.of("clearfolio-fuzz-claims-secret")\n                            : java.util.Optional.empty()/name -> com.clearfolio.viewer.security.CredentialRegistryPort.TENANT_CLAIMS_HMAC_SECRET.equals(name) ? java.util.Optional.of("clearfolio-fuzz-claims-secret") : java.util.Optional.empty()/g' src/test/java/com/clearfolio/viewer/fuzz/TenantClaimsFuzzTest.java
