1.  **Objective**: Optimize string sanitization by replacing `.replace("\u0000", "")` calls with a more efficient single-pass method that delays `StringBuilder` allocation. Since duplicating the logic violates DRY, we will centralize this optimization into a new utility class, e.g., `StringUtils.java` (if it does not exist) or add it to an existing one. Actually, wait, let's just make it a private static helper method inside `ConversionProperties.java`, `ArtifactStoreProperties.java`, etc? The code review explicitly asked to extract it into a shared static helper method (e.g., `StringUtils.removeNullChars(String)`).
2.  **Target Files**:
    *   `src/main/java/com/clearfolio/viewer/util/StringUtils.java` (create new)
    *   `src/main/java/com/clearfolio/viewer/config/ConversionProperties.java`
    *   `src/main/java/com/clearfolio/viewer/config/ArtifactStoreProperties.java`
    *   `src/main/java/com/clearfolio/viewer/model/ConversionJob.java`
    *   `src/main/java/com/clearfolio/viewer/artifact/ArtifactLinkService.java`
    *   `src/main/java/com/clearfolio/viewer/auth/TenantContext.java`
    *   `src/main/java/com/clearfolio/viewer/auth/TenantAccessService.java`
3.  **Changes**:
    *   Create a shared utility class `StringUtils`.
    *   Implement the optimization pattern in `StringUtils.removeNullChars(String value)`.
    *   Update the specific usages in the classes to use `StringUtils.removeNullChars(value)`.
4.  **Journal Entry**: Update `.jules/bolt.md` with the learning about `String.replace()` overhead.
5.  **Tests**: Run `mvn test` and `mvn checkstyle:check` to ensure no functionality is broken and formatting is preserved.
6.  **Pre-commit Steps**: Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
7.  **Submit PR**: Create PR in Korean.
