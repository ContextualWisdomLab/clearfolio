package com.clearfolio.viewer.credential;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CredentialRegistryContractTest {

    @Test
    void registryResolvesPurposeScopedVersionedMaterial() {
        CredentialReference reference = new CredentialReference(
                " tenant-claims-hmac ",
                CredentialPurpose.TENANT_CLAIMS_SIGNING
        );
        CredentialSnapshot expected = new CredentialSnapshot(
                "tenant-claims-hmac",
                "v7",
                CredentialPurpose.TENANT_CLAIMS_SIGNING,
                new byte[] {1, 2, 3, 4}
        );
        CredentialRegistry registry = actual -> {
            assertEquals(reference, actual);
            return expected;
        };

        assertEquals("tenant-claims-hmac", reference.credentialName());
        assertEquals(expected, registry.resolveAdapterMaterial(reference));
        assertEquals(expected, registry.resolveScoped(reference));
        assertEquals("credential-registry-v1", CredentialRegistry.CONTRACT_VERSION);
    }

    @Test
    void registryDoesNotExposeRawResolveAsConsumerFacingMethod() {
        assertThrows(
                NoSuchMethodException.class,
                () -> CredentialRegistry.class.getDeclaredMethod("resolve", CredentialReference.class)
        );
    }

    @Test
    void scopedResolutionRejectsPurposeMismatchWhileKeepingAdapterHookExplicit() {
        CredentialReference reference = new CredentialReference(
                "tenant-claims-hmac",
                CredentialPurpose.TENANT_CLAIMS_SIGNING
        );
        CredentialSnapshot mismatched = new CredentialSnapshot(
                "artifact-token-hmac",
                "v2",
                CredentialPurpose.ARTIFACT_TOKEN_SIGNING,
                new byte[] {5, 4, 3, 2}
        );
        CredentialRegistry registry = ignored -> mismatched;

        assertEquals(mismatched, registry.resolveAdapterMaterial(reference));
        assertThrows(IllegalStateException.class, () -> registry.resolveScoped(reference));
    }

    @Test
    void scopedResolutionRejectsMissingAdapterMaterial() {
        CredentialReference reference = new CredentialReference(
                "tenant-claims-hmac",
                CredentialPurpose.TENANT_CLAIMS_SIGNING
        );
        CredentialRegistry registry = ignored -> null;

        assertThrows(IllegalStateException.class, () -> registry.resolveScoped(reference));
    }

    @Test
    void credentialReferenceFailsClosedForInvalidAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new CredentialReference(
                null,
                CredentialPurpose.TENANT_CLAIMS_SIGNING
        ));
        assertThrows(IllegalArgumentException.class, () -> new CredentialReference(
                "  ",
                CredentialPurpose.TENANT_CLAIMS_SIGNING
        ));
        assertThrows(NullPointerException.class, () -> new CredentialReference("tenant-key", null));
    }

    @Test
    void credentialSnapshotDefensivelyCopiesAndRedactsSecretMaterial() {
        byte[] source = new byte[] {9, 8, 7, 6};
        CredentialSnapshot snapshot = new CredentialSnapshot(
                " artifact-token-hmac ",
                " v2 ",
                CredentialPurpose.ARTIFACT_TOKEN_SIGNING,
                source
        );
        source[0] = 0;

        byte[] first = snapshot.secretBytes();
        byte[] second = snapshot.secretBytes();

        assertArrayEquals(new byte[] {9, 8, 7, 6}, first);
        assertArrayEquals(first, second);
        assertNotSame(first, second);
        first[1] = 0;
        assertArrayEquals(new byte[] {9, 8, 7, 6}, snapshot.secretBytes());
        assertEquals("artifact-token-hmac", snapshot.credentialId());
        assertEquals("v2", snapshot.version());
        assertEquals(CredentialPurpose.ARTIFACT_TOKEN_SIGNING, snapshot.purpose());
        assertFalse(snapshot.toString().contains("9"));
        assertEquals(
                "CredentialSnapshot[credentialId=artifact-token-hmac, version=v2, purpose=ARTIFACT_TOKEN_SIGNING, secret=<redacted>]",
                snapshot.toString()
        );
    }

    @Test
    void credentialSnapshotFailsClosedForInvalidMetadataOrMaterial() {
        assertThrows(IllegalArgumentException.class, () -> new CredentialSnapshot(
                null, "v1", CredentialPurpose.TENANT_CLAIMS_SIGNING, new byte[] {1}
        ));
        assertThrows(IllegalArgumentException.class, () -> new CredentialSnapshot(
                "key", " ", CredentialPurpose.TENANT_CLAIMS_SIGNING, new byte[] {1}
        ));
        assertThrows(NullPointerException.class, () -> new CredentialSnapshot(
                "key", "v1", null, new byte[] {1}
        ));
        assertThrows(IllegalArgumentException.class, () -> new CredentialSnapshot(
                "key", "v1", CredentialPurpose.TENANT_CLAIMS_SIGNING, null
        ));
        assertThrows(IllegalArgumentException.class, () -> new CredentialSnapshot(
                "key", "v1", CredentialPurpose.TENANT_CLAIMS_SIGNING, new byte[0]
        ));
    }
}
