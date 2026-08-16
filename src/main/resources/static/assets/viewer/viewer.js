import { setBusyState } from "./dom-utils.js";

const POLL_DELAY_MS = 1500;
const PDF_JS_MODULE_PATH = "/webjars/pdfjs-dist/6.1.200/build/pdf.mjs";
const PDF_JS_WORKER_PATH = "/webjars/pdfjs-dist/6.1.200/build/pdf.worker.mjs";
const DEMO_AUTH_HEADERS = {
  "X-Clearfolio-Tenant-Id": "buyer-demo",
  "X-Clearfolio-Subject-Id": "buyer-demo-operator",
  "X-Clearfolio-Permissions": "job:read,viewer:read",
};

const el = {
  docMeta: document.getElementById("doc-meta"),
  liveStatus: document.getElementById("live-status"),
  error: document.getElementById("error"),
  errorTitle: document.getElementById("error-title"),
  errorMessage: document.getElementById("error-message"),
  retryBtn: document.getElementById("retry-btn"),
  openJsonLink: document.getElementById("open-json-link"),
  preview: document.getElementById("preview"),
};

let pdfJsModulePromise;
let retryBtnRestore = null;
let currentAttemptId = 0;

function getMetaContent(name) {
  const meta = document.querySelector(`meta[name="${name}"]`);
  if (!meta) {
    return null;
  }
  const raw = meta.getAttribute("content");
  if (!raw) {
    return null;
  }
  const value = raw.trim();
  return value.length > 0 ? value : null;
}

function getDocId() {
  const search = new URLSearchParams(window.location.search);
  const raw = search.get("docId");
  if (raw) {
    return raw.trim();
  }
  return getMetaContent("clearfolio-doc-id");
}

function getInitialState() {
  return getMetaContent("clearfolio-initial-state");
}

function isUuidLike(value) {
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(value);
}

function setLoading(attemptId, message) {
  if (attemptId !== currentAttemptId) return;
  el.error.hidden = true;
  el.liveStatus.textContent = message;
  el.preview.setAttribute("aria-busy", "true");
  if (!retryBtnRestore) {
    retryBtnRestore = setBusyState(el.retryBtn, "Refreshing...");
  }
}

function clearLoading(attemptId) {
  if (attemptId !== currentAttemptId) return;
  if (retryBtnRestore) {
    retryBtnRestore();
    retryBtnRestore = null;
  }
}

function showError(attemptId, message) {
  if (attemptId !== currentAttemptId) return;
  el.error.hidden = false;
  el.errorMessage.textContent = message;
  el.liveStatus.textContent = "";
  el.preview.setAttribute("aria-busy", "false");
  el.errorTitle.focus();
  clearLoading(attemptId);
}

function clearPreview(attemptId) {
  if (attemptId !== currentAttemptId) return;
  const nodes = Array.from(el.preview.querySelectorAll("iframe, img, pre, a, canvas, .pdf-preview-meta"));
  for (const node of nodes) {
    node.remove();
  }

  const skeleton = el.preview.querySelector(".skeleton");
  if (skeleton) {
    skeleton.remove();
  }

  const help = el.preview.querySelector("#preview-help");
  if (help) {
    help.remove();
  }
}

function resolveSameOriginHttpUrl(urlStr) {
  if (typeof urlStr !== "string" || urlStr.trim() === "") {
    return null;
  }
  try {
    const url = new URL(urlStr, window.location.origin);
    const safeProtocol = url.protocol === "http:" || url.protocol === "https:";
    const safeAuthority = url.origin === window.location.origin && url.username === "" && url.password === "";
    return safeProtocol && safeAuthority ? url.href : null;
  } catch (_error) {
    return null;
  }
}

function renderPreviewLink(attemptId, path) {
  if (attemptId !== currentAttemptId) return;
  const resolvedPath = resolveSameOriginHttpUrl(path);
  if (resolvedPath === null) {
    return;
  }
  const link = document.createElement("a");
  link.href = resolvedPath;
  link.textContent = "Open artifact";
  link.className = "btn btn-secondary";
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.setAttribute("aria-label", "Open artifact in a new tab");
  el.preview.appendChild(link);
}

function getPdfJsModule() {
  if (!pdfJsModulePromise) {
    const modulePath = getMetaContent("clearfolio-pdfjs-module-path") || PDF_JS_MODULE_PATH;
    const workerPath = getMetaContent("clearfolio-pdfjs-worker-path") || PDF_JS_WORKER_PATH;
    const resolvedModulePath = resolveSameOriginHttpUrl(modulePath);
    const resolvedWorkerPath = resolveSameOriginHttpUrl(workerPath);
    if (resolvedModulePath === null || resolvedWorkerPath === null) {
      return Promise.reject(new Error("PDF.js asset configuration is invalid"));
    }
    pdfJsModulePromise = import(resolvedModulePath).then(pdfJs => {
      pdfJs.GlobalWorkerOptions.workerSrc = resolvedWorkerPath;
      return pdfJs;
    });
  }
  return pdfJsModulePromise;
}

async function renderPdfInline(attemptId, path) {
  if (attemptId !== currentAttemptId) return;
  const resolvedPath = resolveSameOriginHttpUrl(path);
  if (resolvedPath === null) {
    throw new Error("PDF preview resource is invalid");
  }

  const pdfJs = await getPdfJsModule();
  if (attemptId !== currentAttemptId) return;
  const loadingTask = pdfJs.getDocument({
    url: resolvedPath,
    withCredentials: true,
  });
  const pdfDocument = await loadingTask.promise;
  try {
    if (attemptId !== currentAttemptId) return;
    const page = await pdfDocument.getPage(1);
    if (attemptId !== currentAttemptId) return;
    const unscaledViewport = page.getViewport({ scale: 1 });
    const availableWidth = Math.max(320, el.preview.clientWidth - 32);
    const renderScale = Math.min(2, availableWidth / unscaledViewport.width);
    const viewport = page.getViewport({ scale: renderScale });
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d", { alpha: false });
    if (context === null) {
      throw new Error("Canvas rendering is unavailable");
    }

    canvas.width = Math.ceil(viewport.width);
    canvas.height = Math.ceil(viewport.height);
    canvas.style.width = "100%";
    canvas.style.height = "auto";
    canvas.className = "pdf-preview-canvas";
    canvas.setAttribute("role", "img");
    canvas.setAttribute("aria-label", `Rendered first page of a ${pdfDocument.numPages}-page PDF`);

    await page.render({ canvasContext: context, viewport }).promise;
    if (attemptId !== currentAttemptId) return;
    el.preview.appendChild(canvas);

    const metadata = document.createElement("p");
    metadata.className = "help pdf-preview-meta";
    metadata.textContent = pdfDocument.numPages === 1
      ? "Showing the document page."
      : `Showing page 1 of ${pdfDocument.numPages}. Open the artifact for the complete document.`;
    el.preview.appendChild(metadata);
  } finally {
    await pdfDocument.destroy();
  }
}

async function fetchJson(url, signal) {
  const res = await fetch(url, {
    headers: {
      Accept: "application/json",
      ...DEMO_AUTH_HEADERS,
    },
    credentials: "same-origin",
    signal,
  });

  const contentType = (res.headers.get("content-type") || "").toLowerCase();
  const data = contentType.includes("application/json") ? await res.json() : null;

  return { res, data };
}

async function openJsonDocument(url) {
  const popup = window.open("", "_blank");
  if (!popup) {
    showError(currentAttemptId, "Allow popups to inspect JSON evidence in a new tab.");
    return;
  }

  popup.opener = null;
  popup.document.title = "Clearfolio viewer bootstrap JSON";
  const pre = popup.document.createElement("pre");
  pre.textContent = "Loading...";
  popup.document.body.appendChild(pre);

  const { res, data } = await fetchJson(url);
  pre.textContent = res.ok && data
    ? JSON.stringify(data, null, 2)
    : "Unable to load JSON evidence with the current tenant claim.";
}

async function poll(docId, abortSignal, attemptId) {
  try {
    setLoading(attemptId, "Checking conversion status...");

    const statusUrl = `/api/v1/convert/jobs/${encodeURIComponent(docId)}`;
    const { res, data } = await fetchJson(statusUrl, abortSignal);

    if (abortSignal.aborted) {
      return;
    }

    if (res.status === 404) {
      showError(attemptId, "This document could not be found.");
      return;
    }

    if (!res.ok || !data) {
      showError(attemptId, "Unable to read job status. Please retry.");
      return;
    }

    const status = data.status;
    if (status === "SUBMITTED" || status === "PROCESSING") {
      if (attemptId === currentAttemptId) {
        el.liveStatus.textContent = `${status} - retrying soon...`;
      }
      window.setTimeout(() => {
        if (!abortSignal.aborted) {
          void poll(docId, abortSignal, attemptId);
        }
      }, POLL_DELAY_MS);
      return;
    }

    if (status !== "SUCCEEDED") {
      showError(attemptId, `Preview is not available. Status: ${status}`);
      return;
    }

    setLoading(attemptId, "Loading viewer bootstrap...");
    const viewerUrl = `/api/v1/viewer/${encodeURIComponent(docId)}`;
    const bootstrap = await fetchJson(viewerUrl, abortSignal);

    if (abortSignal.aborted) {
      return;
    }

    if (!bootstrap.res.ok || !bootstrap.data) {
      showError(attemptId, "Viewer bootstrap failed. Please retry.");
      return;
    }

    clearPreview(attemptId);
    const path = bootstrap.data.previewResourcePath;
    if (typeof path === "string" && path.endsWith(".pdf")) {
      await renderPdfInline(attemptId, path);
    }
    if (typeof path === "string" && path.length > 0) {
      renderPreviewLink(attemptId, path);
    }

    if (attemptId === currentAttemptId) {
      el.preview.setAttribute("aria-busy", "false");
      el.liveStatus.textContent = "Ready.";
      clearLoading(attemptId);
    }
  } catch (_error) {
    if (abortSignal.aborted) {
      return;
    }
    showError(attemptId, "Network or rendering error while loading preview. Please retry.");
  }
}

async function init() {
  const docId = getDocId();
  if (!docId) {
    el.docMeta.textContent = "Missing docId.";
    showError(currentAttemptId, "The viewer URL is missing a docId parameter.");
    return;
  }

  if (!isUuidLike(docId)) {
    el.docMeta.textContent = `Invalid docId: ${docId}`;
    showError(currentAttemptId, "The provided docId is invalid.");
    return;
  }

  el.docMeta.textContent = `docId: ${docId}`;

  // External integration mode: when the URL carries a signed artifactToken
  // (issued via POST /api/v1/viewer/{docId}/artifact-links), render the
  // artifact directly and skip the tenant-header bootstrap. Possession of a
  // valid signed token already grants artifact read access.
  const externalArtifactToken = new URLSearchParams(window.location.search).get("artifactToken");
  if (externalArtifactToken) {
    el.retryBtn.hidden = true;
    clearPreview(currentAttemptId);
    const artifactPath = `/artifacts/${encodeURIComponent(docId)}.pdf?artifactToken=${encodeURIComponent(externalArtifactToken)}`;
    try {
      await renderPdfInline(currentAttemptId, artifactPath);
      renderPreviewLink(currentAttemptId, artifactPath);
      el.preview.setAttribute("aria-busy", "false");
      el.liveStatus.textContent = "Ready.";
    } catch (_error) {
      showError(currentAttemptId, "Unable to render the signed artifact preview.");
    }
    return;
  }

  el.openJsonLink.hidden = false;
  el.openJsonLink.href = `/api/v1/viewer/${encodeURIComponent(docId)}`;
  el.openJsonLink.addEventListener("click", event => {
    event.preventDefault();
    void openJsonDocument(el.openJsonLink.href);
  });

  const initialState = getInitialState();

  let controller = new AbortController();
  const start = () => {
    currentAttemptId++;
    controller.abort();
    controller = new AbortController();
    void poll(docId, controller.signal, currentAttemptId);
  };

  el.retryBtn.addEventListener("click", start);
  if (initialState === "NOT_FOUND") {
    showError(currentAttemptId, "This document could not be found.");
    return;
  }
  if (initialState === "FAILED") {
    showError(currentAttemptId, "Preview is not available. Status: FAILED");
    return;
  }

  start();
}

void init();
