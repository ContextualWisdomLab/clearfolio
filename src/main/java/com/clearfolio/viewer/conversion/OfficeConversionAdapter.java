package com.clearfolio.viewer.conversion;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Provider-neutral boundary for sandboxed or remote Office-to-PDF conversion.
 *
 * <p>Implementations own converter-specific transport and process details. The
 * Clearfolio API and job lifecycle depend only on this contract so a sandboxed
 * sidecar, authenticated remote service, or deterministic fixture adapter can
 * be substituted without changing document-delivery authority.</p>
 */
@FunctionalInterface
public interface OfficeConversionAdapter {

    /**
     * Maximum number of linked PDF actions inspected before the output fails closed.
     */
    int MAX_ACTION_CHAIN_DEPTH = 32;

    /**
     * Converts one immutable Office request and verifies its source and result.
     *
     * <p>Before provider invocation, Clearfolio requires the declared source
     * format to be a current Office conversion candidate and requires its leading
     * container signature to match the declared format family. ODF candidates
     * additionally pass bounded, non-networked manifest parsing and root media-type
     * validation. OOXML candidates additionally reject bounded package relationship
     * parts that delegate resource retrieval outside the package. This common
     * preflight is intentionally narrower than complete archive, macro, OLE,
     * malware, or fidelity qualification, which remain sandbox/content-policy
     * responsibilities. After provider execution, the result must be present,
     * source-bound, tied to the exact qualified adapter/runtime, request generation
     * and policy, within request-bound byte/page publication ceilings, and parseable
     * as a non-empty, unencrypted PDF without prohibited active content.</p>
     *
     * @param request immutable tenant-, generation-, and adapter-bound conversion request
     * @return verified PDF result with source, request, and adapter provenance
     * @throws OfficeConversionException when source preflight fails, the provider
     *         returns no result, provenance mismatches, adapter identity is
     *         unexpected, output exceeds limits, output is malformed/encrypted,
     *         prohibited active content is present, or page limits are exceeded
     */
    default OfficeConversionResult convert(OfficeConversionRequest request) {
        OfficeSourceContainerPreflight.requireQualifiedContainer(request);
        OfficeOdfManifestPreflight.requireQualifiedManifest(request);
        OfficeOoxmlRelationshipPreflight.requireNoExternalRelationships(request);
        OfficeConversionResult result = performConversion(request);
        if (result == null) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion adapter returned no result"
            );
        }
        if (!request.sourceSha256().equals(result.sourceSha256())) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion result source digest mismatch"
            );
        }
        if (!request.expectedAdapterId().equals(result.adapterId())
                || !request.expectedAdapterVersion().equals(result.adapterVersion())) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion result adapter identity mismatch"
            );
        }
        if (!request.binding().equals(result.requestBinding())) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion result request binding mismatch"
            );
        }

        byte[] pdfBytes = result.pdfBytes();
        if (pdfBytes.length > request.maxOutputBytes()) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.OUTPUT_LIMIT_EXCEEDED,
                    "conversion output exceeds maximum bytes"
            );
        }
        requireParseablePdf(pdfBytes, request.maxPdfPages());
        return result;
    }

    /**
     * Performs provider-specific conversion before Clearfolio validates result provenance.
     *
     * @param request immutable tenant- and generation-bound conversion request
     * @return provider result, which the default conversion authority validates
     */
    OfficeConversionResult performConversion(OfficeConversionRequest request);

    private static void requireParseablePdf(byte[] pdfBytes, int maxPdfPages) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new OfficeConversionException(
                        OfficeConversionFailureCode.INVALID_OUTPUT,
                        "conversion output PDF must not be encrypted"
                );
            }
            if (containsProhibitedActiveContent(document)) {
                throw new OfficeConversionException(
                        OfficeConversionFailureCode.POLICY_DENIED,
                        "conversion output contains prohibited active content"
                );
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new OfficeConversionException(
                        OfficeConversionFailureCode.INVALID_OUTPUT,
                        "conversion output PDF has no pages"
                );
            }
            if (pageCount > maxPdfPages) {
                throw new OfficeConversionException(
                        OfficeConversionFailureCode.PAGE_LIMIT_EXCEEDED,
                        "conversion output exceeds maximum pages"
                );
            }
        } catch (IOException ex) {
            throw new OfficeConversionException(
                    OfficeConversionFailureCode.INVALID_OUTPUT,
                    "conversion output is not a valid PDF"
            );
        }
    }

    private static boolean containsProhibitedActiveContent(PDDocument document) {
        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        COSBase openAction = catalog.getDictionaryObject(COSName.getPDFName("OpenAction"));
        if (openAction != null && isProhibitedOpenAction(openAction)) {
            return true;
        }
        if (containsProhibitedAdditionalActions(
                catalog.getDictionaryObject(COSName.getPDFName("AA")))) {
            return true;
        }
        if (catalog.getDictionaryObject(COSName.getPDFName("AF")) != null) {
            return true;
        }

        COSBase namesBase = catalog.getDictionaryObject(COSName.getPDFName("Names"));
        if (namesBase != null) {
            if (!(namesBase instanceof COSDictionary names)) {
                return true;
            }
            if (names.getDictionaryObject(COSName.getPDFName("JavaScript")) != null
                    || names.getDictionaryObject(COSName.getPDFName("EmbeddedFiles")) != null) {
                return true;
            }
        }

        for (PDPage page : document.getPages()) {
            if (pageContainsProhibitedActiveContent(page)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProhibitedOpenAction(COSBase openAction) {
        if (isInternalDestination(openAction)) {
            return false;
        }
        return isProhibitedAction(openAction, false, newIdentitySet(), 0);
    }

    private static boolean isInternalDestination(COSBase destination) {
        return destination instanceof COSArray
                || destination instanceof COSName
                || destination instanceof COSString;
    }

    private static boolean pageContainsProhibitedActiveContent(PDPage page) {
        COSDictionary pageDictionary = page.getCOSObject();
        if (containsProhibitedAdditionalActions(
                pageDictionary.getDictionaryObject(COSName.getPDFName("AA")))) {
            return true;
        }

        COSBase annotationsBase = pageDictionary.getDictionaryObject(COSName.getPDFName("Annots"));
        if (annotationsBase == null) {
            return false;
        }
        if (!(annotationsBase instanceof COSArray annotations)) {
            return true;
        }
        for (int index = 0; index < annotations.size(); index++) {
            COSBase annotationBase = annotations.getObject(index);
            if (!(annotationBase instanceof COSDictionary annotation)) {
                return true;
            }
            if (annotationContainsProhibitedActiveContent(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean annotationContainsProhibitedActiveContent(COSDictionary annotation) {
        if (containsProhibitedAdditionalActions(
                annotation.getDictionaryObject(COSName.getPDFName("AA")))) {
            return true;
        }
        COSBase actionBase = annotation.getDictionaryObject(COSName.getPDFName("A"));
        if (actionBase == null) {
            return false;
        }
        return isProhibitedAction(actionBase, true, newIdentitySet(), 0);
    }

    private static boolean containsProhibitedAdditionalActions(COSBase additionalActionsBase) {
        if (additionalActionsBase == null) {
            return false;
        }
        if (!(additionalActionsBase instanceof COSDictionary additionalActions)) {
            return true;
        }

        // PDF /AA entries are event-triggered actions rather than explicit user
        // navigation. Preserve an empty dictionary for interoperability, but fail
        // closed when any trigger is configured regardless of the nested action
        // type. A benign direct /A GoTo may remain fidelity-preserving; an /AA
        // GoTo can execute automatically on page/document/annotation events.
        return !additionalActions.keySet().isEmpty();
    }

    private static boolean isProhibitedAction(
            COSBase actionBase,
            boolean allowUri,
            Set<COSBase> visited,
            int depth
    ) {
        if (!(actionBase instanceof COSDictionary action)
                || depth >= MAX_ACTION_CHAIN_DEPTH
                || !visited.add(action)) {
            return true;
        }

        COSBase actionType = action.getDictionaryObject(COSName.getPDFName("S"));
        boolean allowedType = COSName.getPDFName("GoTo").equals(actionType)
                || (allowUri && COSName.getPDFName("URI").equals(actionType));
        if (!allowedType) {
            return true;
        }
        if (COSName.getPDFName("GoTo").equals(actionType)
                && action.getDictionaryObject(COSName.getPDFName("D")) == null) {
            return true;
        }
        if (COSName.getPDFName("URI").equals(actionType)
                && !isAllowedUriAction(action)) {
            return true;
        }

        COSBase next = action.getDictionaryObject(COSName.getPDFName("Next"));
        if (next == null) {
            return false;
        }
        if (next instanceof COSArray chainedActions) {
            for (int index = 0; index < chainedActions.size(); index++) {
                if (isProhibitedAction(chainedActions.getObject(index), allowUri, visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        return isProhibitedAction(next, allowUri, visited, depth + 1);
    }

    private static boolean isAllowedUriAction(COSDictionary action) {
        COSBase uriBase = action.getDictionaryObject(COSName.getPDFName("URI"));
        if (!(uriBase instanceof COSString uriString)) {
            return false;
        }
        try {
            URI uri = new URI(uriString.getString());
            String scheme = uri.getScheme();
            if ("mailto".equalsIgnoreCase(scheme)) {
                return true;
            }
            boolean webScheme = "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme);
            return webScheme && !uri.isOpaque() && uri.getHost() != null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private static Set<COSBase> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
