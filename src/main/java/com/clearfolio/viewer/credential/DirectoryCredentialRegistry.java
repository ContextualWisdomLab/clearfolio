package com.clearfolio.viewer.credential;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Read-only standalone credential registry backed by a bounded directory layout.
 *
 * <p>Each credential is stored under a safe logical name with an exact purpose,
 * an exact active-version marker, and versioned opaque key bytes. Symbolic links
 * are rejected at credential and key-file boundaries so a local registry cannot
 * silently escape its configured root. The adapter never logs or renders secret
 * bytes.</p>
 */
public final class DirectoryCredentialRegistry implements CredentialRegistry {

    private static final int MAX_METADATA_BYTES = 256;
    private static final int MAX_SECRET_BYTES = 64 * 1024;
    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final Path rootDirectory;

    /**
     * Creates a local read-only credential registry rooted at one real directory.
     *
     * @param rootDirectory registry root that must already exist and must not be a symbolic link
     * @throws NullPointerException when the root path is absent
     * @throws IllegalArgumentException when the root is not a real directory
     */
    public DirectoryCredentialRegistry(Path rootDirectory) {
        Path normalizedRoot = Objects.requireNonNull(rootDirectory, "rootDirectory")
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("credential registry root must be a directory");
        }
        this.rootDirectory = normalizedRoot;
    }

    /**
     * Resolves the currently active version of one purpose-scoped credential.
     *
     * @param reference server-owned credential reference
     * @return immutable snapshot containing a defensive copy of bounded secret bytes
     * @throws NullPointerException when the reference is absent
     * @throws IllegalArgumentException when a credential or version identifier is unsafe
     * @throws IllegalStateException when registry state is missing, malformed, mismatched, or unsafe
     */
    @Override
    public CredentialSnapshot resolve(CredentialReference reference) {
        CredentialReference required = Objects.requireNonNull(reference, "reference");
        String credentialName = requireSafeIdentifier(required.credentialName(), "credentialName");
        Path credentialDirectory = requireDirectory(
                rootDirectory.resolve(credentialName).normalize(),
                "credential directory unavailable"
        );

        String purposeText = readMetadata(
                credentialDirectory.resolve("purpose"),
                "credential purpose unavailable"
        );
        CredentialPurpose storedPurpose = parsePurpose(purposeText);
        if (storedPurpose != required.purpose()) {
            throw new IllegalStateException("credential purpose mismatch");
        }

        String versionText = readMetadata(
                credentialDirectory.resolve("active-version"),
                "credential version unavailable"
        );
        String version = requireSafeIdentifier(versionText, "credentialVersion");
        Path versionsDirectory = requireDirectory(
                credentialDirectory.resolve("versions"),
                "credential versions unavailable"
        );
        byte[] secretBytes = readBounded(
                versionsDirectory.resolve(version + ".key"),
                MAX_SECRET_BYTES,
                "credential material unavailable"
        );
        if (secretBytes.length == 0) {
            throw new IllegalStateException("credential material unavailable");
        }

        return new CredentialSnapshot(
                credentialName,
                version,
                storedPurpose,
                secretBytes
        );
    }

    private static CredentialPurpose parsePurpose(String purposeText) {
        try {
            return CredentialPurpose.valueOf(purposeText);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("credential purpose invalid", exception);
        }
    }

    private static String readMetadata(Path path, String errorMessage) {
        byte[] bytes = readBounded(path, MAX_METADATA_BYTES, errorMessage);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalStateException(errorMessage);
        }
        return value;
    }

    private static byte[] readBounded(Path path, int maximumBytes, String errorMessage) {
        try (InputStream inputStream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = inputStream.readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes) {
                throw new IllegalStateException(errorMessage);
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private static Path requireDirectory(Path path, String errorMessage) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorMessage);
        }
        return path;
    }

    private static String requireSafeIdentifier(String value, String fieldName) {
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " is not a safe identifier");
        }
        return value;
    }
}
