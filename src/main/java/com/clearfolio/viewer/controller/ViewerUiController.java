package com.clearfolio.viewer.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * HTML viewer UI entrypoint.
 */
@RestController
public class ViewerUiController {

    // Keep this in sync with `pom.xml` pdfjs-dist version.
    static final String PDF_JS_VIEWER_PATH = "/webjars/pdfjs-dist/4.10.38/web/viewer.html";
    private static final String INVALID_DOC_ID_SENTINEL = "invalid";

    /**
     * Returns the buyer-demo document intake shell.
     *
     * @return HTML payload for document intake and session history
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> home() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(demoShellHtml());
    }

    /**
     * Returns an HTML viewer shell.
     *
     * @param docId document identifier
     * @return HTML payload or redirect
     */
    @GetMapping(value = "/viewer/{docId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewer(@PathVariable String docId) {
        try {
            UUID parsed = UUID.fromString(docId);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(viewerShellHtml(parsed.toString(), "LOADING"));
        } catch (IllegalArgumentException ex) {
            // Friendly invalid-document shell (instead of a raw framework 404)
            // so integrator deep links, e.g. an admin console linking
            // /viewer/{docId}, land on a readable page.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(viewerShellHtml(INVALID_DOC_ID_SENTINEL, "NOT_FOUND"));
        }
    }

    private static String escapeHtmlAttribute(String value) {
        return HtmlUtils.htmlEscape(value);
    }

    // ⚡ Bolt: Use static template parts to avoid chained replace()
    // allocations on every request.

    /** Part 1 of the viewer shell template. */
    private static final String VIEWER_SHELL_PART_1 = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
                <meta name="referrer" content="no-referrer" />
                <meta name="clearfolio-doc-id" content=\"""";

    /** Part 2 of the viewer shell template. */
    private static final String VIEWER_SHELL_PART_2 = """
            " />
                <meta name="clearfolio-initial-state" content=\"""";

    /** Part 3 of the viewer shell template. */
    private static final String VIEWER_SHELL_PART_3 = """
            " />
                <meta name="clearfolio-pdfjs-viewer-path" content=\"""";

    /** Part 4 of the viewer shell template. */
    private static final String VIEWER_SHELL_PART_4 = """
            " />
                <title>Clearfolio Viewer</title>
                <link rel="stylesheet" href="/assets/viewer/viewer.css" />
              </head>
              <body>
                <a class="skip-link" href="#main">Skip to content</a>

                <header class="app-header" role="banner">
                  <div class="app-header__inner">
                    <div class="brand" aria-label="Clearfolio Viewer">
                      <span class="brand__name">Clearfolio Viewer</span>
                    </div>

                    <nav class="header-nav" aria-label="Viewer utilities">
                      <a class="header-nav__link" href="/healthz">Service status</a>
                    </nav>
                  </div>
                </header>

                <main id="main" class="app-main" tabindex="-1">
                  <h1 class="page-title">Document preview</h1>
                  <p class="page-subtitle" id="doc-meta">Preparing preview shell...</p>

                  <section class="panel" aria-labelledby="state-title">
                    <h2 id="state-title" class="panel__title">Preview status</h2>

                    <div id="live-status" class="status" role="status" aria-live="polite" aria-atomic="true">Loading...</div>

                    <div id="error" class="error" role="alert" hidden>
                      <h3 class="error__title" id="error-title" tabindex="-1">Unable to load preview</h3>
                      <p class="error__message" id="error-message"></p>
                    </div>

                    <div class="actions" aria-label="Actions">
                      <button type="button" class="btn btn-primary" id="retry-btn">Refresh</button>
                      <a class="btn btn-secondary" id="open-json-link" href="#" target="_blank" rel="noopener noreferrer" aria-label="Open JSON bootstrap in a new tab" hidden>Open JSON bootstrap</a>
                    </div>
                  </section>

                  <section class="panel" aria-labelledby="preview-title">
                    <h2 id="preview-title" class="panel__title">Preview</h2>

                    <div id="preview" class="preview" aria-busy="true">
                      <div class="skeleton" aria-hidden="true"></div>
                      <p class="help" id="preview-help">When ready, the converted artifact will appear here.</p>
                    </div>
                  </section>
                </main>

                <footer class="app-footer" role="contentinfo">
                  <div class="app-footer__inner">
                    <small>Copyright (c) 2026 by HYOSUNG. All rights reserved.</small>
                  </div>
                </footer>

                <script type="module" src="/assets/viewer/viewer.js"></script>
              </body>
            </html>
            """;

    private static String viewerShellHtml(final String docId, final String initialState) {
        String docIdString = escapeHtmlAttribute(docId);
        return VIEWER_SHELL_PART_1 + docIdString + VIEWER_SHELL_PART_2 + initialState
                + VIEWER_SHELL_PART_3 + PDF_JS_VIEWER_PATH + VIEWER_SHELL_PART_4;
    }

    private static String demoShellHtml() {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
                    <meta name="referrer" content="no-referrer" />
                    <title>Clearfolio Viewer</title>
                    <link rel="stylesheet" href="/assets/viewer/viewer.css" />
                  </head>
                  <body>
                    <a class="skip-link" href="#main">Skip to content</a>

                    <header class="app-header" role="banner">
                      <div class="app-header__inner">
                        <div class="brand" aria-label="Clearfolio Viewer">
                          <span class="brand__name">Clearfolio Viewer</span>
                        </div>

                        <nav class="header-nav" aria-label="Viewer utilities">
                          <a class="header-nav__link" href="/healthz">Service status</a>
                        </nav>
                      </div>
                    </header>

                    <main id="main" class="app-main" tabindex="-1">
                      <h1 class="page-title">Document intake</h1>
                      <p class="page-subtitle">Submit a document, watch conversion progress, and open the governed preview from one buyer-demo surface.</p>

                      <section class="panel demo-panel" aria-labelledby="upload-title">
                        <div class="panel-header">
                          <div>
                            <h2 id="upload-title" class="panel__title">Upload document</h2>
                            <p class="panel__caption">Uses the existing async conversion API. History is stored only in this browser session.</p>
                          </div>
                          <button type="button" class="btn btn-secondary btn-compact" id="load-demo-data-btn">Load demo story</button>
                        </div>

                        <form id="upload-form" class="upload-form" enctype="multipart/form-data">
                          <label class="field-label" for="file-input">Document</label>
                          <input id="file-input" name="file" class="file-input" type="file" required />

                          <div class="actions">
                            <button type="submit" class="btn btn-primary" id="submit-btn">Submit document</button>
                          </div>
                        </form>

                        <div id="demo-status" class="status" role="status" aria-live="polite" aria-atomic="true">Ready for upload.</div>
                        <div id="demo-error" class="error" role="alert" hidden>
                          <h3 class="error__title" id="demo-error-title" tabindex="-1">Upload could not continue</h3>
                          <p class="error__message" id="demo-error-message"></p>
                        </div>
                      </section>

                      <section class="kpi-strip" id="kpi-strip" aria-label="Conversion KPIs">
                        <div class="kpi">
                          <span class="kpi__label">Runtime jobs</span>
                          <strong class="kpi__value" id="kpi-total">0</strong>
                        </div>
                        <div class="kpi">
                          <span class="kpi__label">Ready</span>
                          <strong class="kpi__value" id="kpi-ready">0</strong>
                        </div>
                        <div class="kpi">
                          <span class="kpi__label">Success rate</span>
                          <strong class="kpi__value" id="kpi-success-rate">0%</strong>
                        </div>
                        <div class="kpi">
                          <span class="kpi__label">P95 preview</span>
                          <strong class="kpi__value" id="kpi-p95">n/a</strong>
                        </div>
                      </section>

                      <section class="panel evidence-panel" aria-labelledby="kpi-evidence-title">
                        <div class="panel-header">
                          <div>
                            <h2 id="kpi-evidence-title" class="panel__title">KPI snapshot evidence</h2>
                            <p class="panel__caption">Tenant-scoped local evidence from authorized KPI snapshot exports.</p>
                          </div>
                          <button type="button" class="btn btn-secondary btn-compact" id="refresh-evidence-btn">Refresh evidence</button>
                        </div>

                        <dl class="evidence-summary" aria-label="KPI snapshot export evidence">
                          <div class="evidence-summary__item">
                            <dt>Exports</dt>
                            <dd id="kpi-export-count">0</dd>
                          </div>
                          <div class="evidence-summary__item">
                            <dt>Latest export</dt>
                            <dd id="kpi-export-latest">n/a</dd>
                          </div>
                          <div class="evidence-summary__item">
                            <dt>Subject</dt>
                            <dd id="kpi-export-subject">n/a</dd>
                          </div>
                          <div class="evidence-summary__item">
                            <dt>Runtime jobs</dt>
                            <dd id="kpi-export-jobs">0</dd>
                          </div>
                        </dl>
                        <p class="panel__caption" id="kpi-export-status">Snapshot evidence has not been loaded.</p>
                      </section>

                      <section class="panel recovery-panel" aria-labelledby="operator-recovery-title">
                        <div class="panel-header">
                          <div>
                            <h2 id="operator-recovery-title" class="panel__title">Operator recovery evidence</h2>
                            <p class="panel__caption">Buyer-readable recovery posture from the current demo session.</p>
                          </div>
                        </div>

                        <dl class="evidence-summary" aria-label="Operator recovery evidence">
                          <div class="evidence-summary__item">
                            <dt>Needs action</dt>
                            <dd id="recovery-needs-action">0</dd>
                          </div>
                          <div class="evidence-summary__item">
                            <dt>Retry-ready</dt>
                            <dd id="recovery-retry-ready">0</dd>
                          </div>
                          <div class="evidence-summary__item">
                            <dt>Last retry</dt>
                            <dd id="recovery-last-action">n/a</dd>
                          </div>
                          <div class="evidence-summary__item">
                            <dt>Latest inspected</dt>
                            <dd id="recovery-latest-inspected">n/a</dd>
                          </div>
                        </dl>
                        <p class="panel__caption" id="recovery-status">No recovery evidence has been collected in this session.</p>
                      </section>

                      <section class="panel" aria-labelledby="history-title">
                        <div class="panel-header">
                          <div>
                            <h2 id="history-title" class="panel__title">Session history</h2>
                            <p class="panel__caption">Open status JSON for diligence or launch the preview when conversion is ready.</p>
                          </div>
                          <button type="button" class="btn btn-secondary btn-compact" id="clear-history-btn">Clear</button>
                        </div>

                        <div class="table-wrap" id="session-history">
                          <table class="history-table">
                            <thead>
                              <tr>
                                <th scope="col">File</th>
                                <th scope="col">Status</th>
                                <th scope="col">Submitted</th>
                                <th scope="col">Actions</th>
                              </tr>
                            </thead>
                            <tbody id="history-body"></tbody>
                          </table>
                          <p class="empty-state" id="empty-history">No documents submitted in this session.</p>
                        </div>

                        <aside class="job-detail" id="job-detail" aria-labelledby="job-detail-title" hidden>
                          <div class="job-detail__header">
                            <div>
                              <h3 id="job-detail-title" class="job-detail__title">Job detail</h3>
                              <p class="job-detail__caption" id="job-detail-caption">Select a document to inspect operational evidence.</p>
                            </div>
                            <button type="button" class="btn btn-secondary btn-compact" id="retry-job-btn" hidden>Retry dead-lettered job</button>
                          </div>
                          <dl class="job-detail__list" id="job-detail-body"></dl>
                        </aside>
                      </section>
                    </main>

                    <footer class="app-footer" role="contentinfo">
                      <div class="app-footer__inner">
                        <small>Copyright (c) 2026 by HYOSUNG. All rights reserved.</small>
                      </div>
                    </footer>

                    <script type="module" src="/assets/viewer/demo.js"></script>
                  </body>
                </html>
                """;
    }
}
