# Credential registry key-management grounding

## Decision

The provider-neutral credential registry boundary binds each server-owned logical credential name to the returned credential identifier and `CredentialPurpose`. Resolution fails closed when material is missing, the logical identity differs, or the cryptographic purpose differs. The provider-returned version remains metadata for later rotation and audit integration.

This keeps provider translation inside the adapter boundary: if a backing KMS, HSM, database, or managed-secret service uses a provider-specific alias, the adapter must map that alias back to the server-owned logical credential identifier before returning a `CredentialSnapshot`. Runtime consumers do not accept ambiguous or substituted identities.

## Standards traceability

NIST SP 800-57 Part 1 Rev. 5 treats key-control metadata such as key identifiers and intended usage as information that must be protected so cryptographic keying material is used correctly. It also recommends that a key generally be used for only one purpose. These controls support Clearfolio's strict logical-identity binding and distinct `CredentialPurpose` values for tenant-claims signing and artifact-token signing.

Revision 5 remains the current final Part 1 publication. NIST published Revision 6 only as an Initial Public Draft on December 5, 2025; its public-comment period closed on February 5, 2026. The final-revision contract therefore continues to cite Revision 5 rather than silently adopting draft guidance.

This PR defines only the provider-neutral contract. Persistence, KMS/HSM integration, rotation/compatibility windows, audit-record writes, Spring bootstrap, and runtime migration of `TenantAccessService` and `ArtifactLinkService` remain owned by issue #319.

## Source artifact provenance

- Official publication page: <https://www.nist.gov/publications/recommendation-key-management-part-1-general-1>
- Official PDF: <https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-57pt1r5.pdf>
- DOI: <https://doi.org/10.6028/NIST.SP.800-57pt1r5>
- Redistribution status: the publication itself states that it is not subject to copyright in the United States and that attribution is appreciated by NIST. NIST's Research Library likewise states that NIST Federal-government publications are generally public-domain works in the United States and may be reprinted or copied with source credit.

`AGENTS.md` asks substantive PRs to commit paper PDFs when redistribution is permissible. The repository does not currently contain this NIST PDF under `docs/papers/`, so this note deliberately does not invent a repository-relative attachment. Promotion remains blocked on the open review requirement until the actual binary artifact is committed and linked here, or the repository rule is changed through its canonical governance path.

## APA 7 reference

Barker, E. (2020). *Recommendation for key management: Part 1—General* (NIST Special Publication 800-57 Part 1 Rev. 5). National Institute of Standards and Technology. <https://doi.org/10.6028/NIST.SP.800-57pt1r5>
