const STORAGE_KEY = "clearfolio-demo-history-v1";
const KPI_ENDPOINT = "/api/v1/analytics/kpi-snapshot";
const KPI_EXPORTS_ENDPOINT = "/api/v1/analytics/kpi-snapshot-exports";
const DEMO_FIXTURE_URL = "/assets/viewer/demo-fixtures.json";
const POLL_DELAY_MS = 1500;
const ACTIVE_STATUSES = new Set(["ACCEPTED", "SUBMITTED", "PROCESSING"]);
const DEMO_AUTH_HEADERS = {
  "X-Clearfolio-Tenant-Id": "buyer-demo",
  "X-Clearfolio-Subject-Id": "buyer-demo-operator",
  "X-Clearfolio-Permissions": "job:create,job:read,job:retry,viewer:read,artifact-link:create,artifact-link:revoke,audit:read,analytics:read",
};

const el = {
  form: document.getElementById("upload-form"),
  fileInput: document.getElementById("file-input"),
  submitBtn: document.getElementById("submit-btn"),
  status: document.getElementById("demo-status"),
  error: document.getElementById("demo-error"),
  errorMessage: document.getElementById("demo-error-message"),
  errorTitle: document.getElementById("demo-error-title"),
  loadDemoDataBtn: document.getElementById("load-demo-data-btn"),
  historyBody: document.getElementById("history-body"),
  emptyHistory: document.getElementById("empty-history"),
  clearHistoryBtn: document.getElementById("clear-history-btn"),
  kpiTotal: document.getElementById("kpi-total"),
  kpiReady: document.getElementById("kpi-ready"),
  kpiSuccessRate: document.getElementById("kpi-success-rate"),
  kpiP95: document.getElementById("kpi-p95"),
  kpiExportCount: document.getElementById("kpi-export-count"),
  kpiExportLatest: document.getElementById("kpi-export-latest"),
  kpiExportSubject: document.getElementById("kpi-export-subject"),
  kpiExportJobs: document.getElementById("kpi-export-jobs"),
  kpiExportStatus: document.getElementById("kpi-export-status"),
  refreshEvidenceBtn: document.getElementById("refresh-evidence-btn"),
  recoveryNeedsAction: document.getElementById("recovery-needs-action"),
  recoveryRetryReady: document.getElementById("recovery-retry-ready"),
  recoveryLastAction: document.getElementById("recovery-last-action"),
  recoveryLatestInspected: document.getElementById("recovery-latest-inspected"),
  recoveryStatus: document.getElementById("recovery-status"),
  jobDetail: document.getElementById("job-detail"),
  jobDetailCaption: document.getElementById("job-detail-caption"),
  jobDetailBody: document.getElementById("job-detail-body"),
  retryJobBtn: document.getElementById("retry-job-btn"),
};

let activeJobDetail = null;

function loadHistory() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch (err) {
    return [];
  }
}

function saveHistory(history) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(history.slice(0, 12)));
}

function setStatus(message) {
  el.error.hidden = true;
  el.status.textContent = message;
}

function setError(message) {
  el.error.hidden = false;
  el.errorMessage.textContent = message;
  el.status.textContent = "";
  el.errorTitle.focus();
}

function updateJob(jobId, patch, { refreshKpisAfterUpdate = true } = {}) {
  const history = loadHistory();
  const next = history.map(job => (job.jobId === jobId ? { ...job, ...patch } : job));
  saveHistory(next);
  renderHistory(next);
  if (refreshKpisAfterUpdate) {
    void refreshKpis();
  }
}

function createLink(href, label) {
  const link = document.createElement("a");
  link.href = href;
  link.textContent = label;
  link.className = "table-link";
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  return link;
}

async function openJsonDocument(url, title) {
  const popup = window.open("", "_blank");
  if (!popup) {
    setError("Allow popups to inspect JSON evidence in a new tab.");
    return;
  }

  popup.opener = null;
  popup.document.title = title;
  const pre = popup.document.createElement("pre");
  pre.textContent = "Loading...";
  popup.document.body.appendChild(pre);

  const { res, data } = await fetchJson(url);
  pre.textContent = res.ok && data
    ? JSON.stringify(data, null, 2)
    : "Unable to load JSON evidence with the current tenant claim.";
}

function createActionButton(label, onClick) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.className = "btn btn-secondary btn-compact";
  button.addEventListener("click", onClick);
  return button;
}

function jsonHeaders(extra = {}) {
  return {
    Accept: "application/json",
    ...DEMO_AUTH_HEADERS,
    ...extra,
  };
}

function renderHistory(history = loadHistory()) {
  el.historyBody.textContent = "";
  el.emptyHistory.hidden = history.length > 0;

  for (const job of history) {
    const row = document.createElement("tr");
    const fileCell = document.createElement("td");
    const statusCell = document.createElement("td");
    const submittedCell = document.createElement("td");
    const actionsCell = document.createElement("td");

    fileCell.textContent = job.fileName || "Document";
    statusCell.textContent = job.status || "SUBMITTED";
    submittedCell.textContent = job.submittedAt || "";
    actionsCell.className = "table-actions";

    if (job.statusUrl) {
      actionsCell.appendChild(createActionButton("Details", (e) => {
        const btn = e.currentTarget;
        const initialChildren = Array.from(btn.childNodes);
        btn.disabled = true;
        btn.textContent = "Loading...";
        openJobDetail(job).finally(() => {
          btn.replaceChildren(...initialChildren);
          btn.disabled = false;
        });
      }));
      actionsCell.appendChild(createActionButton("Status JSON", () => {
        void openJsonDocument(job.statusUrl, "Clearfolio status JSON");
      }));
    }
    if (job.jobId) {
      actionsCell.appendChild(createLink(`/viewer/${encodeURIComponent(job.jobId)}`, "Open viewer"));
    }

    row.append(fileCell, statusCell, submittedCell, actionsCell);
    el.historyBody.appendChild(row);
  }

  renderRecoveryEvidence(history);
}

function addDetailRow(label, value) {
  const term = document.createElement("dt");
  const description = document.createElement("dd");
  term.textContent = label;
  description.textContent = formatDetailValue(value);
  el.jobDetailBody.append(term, description);
}

function formatDetailValue(value) {
  if (value === null || value === undefined || value === "") {
    return "n/a";
  }

  if (typeof value === "boolean") {
    return value ? "Yes" : "No";
  }

  return String(value);
}

function renderJobDetail(detail) {
  activeJobDetail = detail;
  el.jobDetail.hidden = false;
  el.jobDetailCaption.textContent = detail.fileName
    ? `${detail.fileName} operational evidence`
    : "Operational evidence";
  el.jobDetailBody.textContent = "";

  addDetailRow("Job ID", detail.jobId);
  addDetailRow("Tenant", detail.tenantId);
  addDetailRow("Status", detail.status);
  addDetailRow("Message", detail.message);
  addDetailRow("Attempts", `${detail.attemptCount ?? 0} / ${detail.maxAttempts ?? "n/a"}`);
  addDetailRow("Dead-lettered", Boolean(detail.deadLettered));
  addDetailRow("Retry at", detail.retryAt);
  addDetailRow("Created", detail.createdAt);
  addDetailRow("Started", detail.startedAt);
  addDetailRow("Completed", detail.completedAt);
  addDetailRow("Artifact", detail.convertedResourcePath);

  el.retryJobBtn.hidden = !detail.deadLettered;
}

function isNeedsAction(job) {
  return job.deadLettered === true || job.status === "FAILED" || job.status === "UNSUPPORTED_FORMAT";
}

function renderRecoveryEvidence(history = loadHistory()) {
  const needsAction = history.filter(isNeedsAction).length;
  const retryReady = history.filter(job => job.deadLettered === true).length;
  const latestRecovery = latestByTimestamp(history, "lastRecoveryAt");
  const latestInspected = latestByTimestamp(history, "lastInspectedAt");

  el.recoveryNeedsAction.textContent = String(needsAction);
  el.recoveryRetryReady.textContent = String(retryReady);
  el.recoveryLastAction.textContent = latestRecovery
    ? formatDetailValue(latestRecovery.lastRecoveryAction || latestRecovery.lastRecoveryAt)
    : "n/a";
  el.recoveryLatestInspected.textContent = latestInspected
    ? formatTimestamp(latestInspected.lastInspectedAt)
    : "n/a";
  el.recoveryStatus.textContent = retryReady > 0
    ? "Dead-lettered jobs are retry-ready from the job detail drawer."
    : "No dead-lettered retry action is pending in this session.";
}

function latestByTimestamp(history, fieldName) {
  return history.reduce((latest, job) => {
    if (!job[fieldName]) {
      return latest;
    }
    if (!latest || new Date(job[fieldName]).getTime() > new Date(latest[fieldName]).getTime()) {
      return job;
    }
    return latest;
  }, null);
}

async function openJobDetail(job) {
  if (job.seededDetail) {
    renderJobDetail(job.seededDetail);
    updateJob(job.jobId, {
      status: job.seededDetail.status,
      attemptCount: job.seededDetail.attemptCount,
      maxAttempts: job.seededDetail.maxAttempts,
      retryAt: job.seededDetail.retryAt,
      deadLettered: Boolean(job.seededDetail.deadLettered),
      message: job.seededDetail.message,
      lastInspectedAt: new Date().toISOString(),
    }, {
      refreshKpisAfterUpdate: false,
    });
    setStatus("Seeded job detail loaded.");
    return;
  }

  if (!job.statusUrl) {
    return;
  }

  setStatus("Loading job detail...");
  const { res, data } = await fetchJson(job.statusUrl);
  if (!res.ok || !data) {
    setError("Unable to load job detail. Open the status JSON for raw evidence.");
    return;
  }

  const statusUrl = job.statusUrl || `/api/v1/convert/jobs/${encodeURIComponent(data.jobId)}`;
  updateJob(data.jobId, {
    status: data.status,
    statusUrl,
    attemptCount: data.attemptCount,
    maxAttempts: data.maxAttempts,
    retryAt: data.retryAt,
    deadLettered: Boolean(data.deadLettered),
    message: data.message,
    lastInspectedAt: new Date().toISOString(),
  });
  renderJobDetail(data);
  setStatus("Job detail loaded.");
}

async function retryActiveJob() {
  if (!activeJobDetail || !activeJobDetail.deadLettered) {
    return;
  }

  const jobId = activeJobDetail.jobId;
  const initialChildren = Array.from(el.retryJobBtn.childNodes);
  el.retryJobBtn.disabled = true;
  el.retryJobBtn.setAttribute("aria-busy", "true");
  el.retryJobBtn.textContent = "Retrying...";
  setStatus("Requesting operator retry...");

  try {
    const res = await fetch(`/api/v1/convert/jobs/${encodeURIComponent(jobId)}/retry`, {
      method: "POST",
      credentials: "same-origin",
      headers: jsonHeaders({
        "X-Clearfolio-Operator-Id": "buyer-demo-operator",
      }),
    });
    const data = (res.headers.get("content-type") || "").includes("application/json") ? await res.json() : null;

    if (!res.ok || !data) {
      setError((data && data.message) || "Retry could not be accepted.");
      return;
    }

    const statusUrl = data.statusUrl || `/api/v1/convert/jobs/${encodeURIComponent(jobId)}`;
    updateJob(jobId, {
      status: data.status || "ACCEPTED",
      statusUrl,
      deadLettered: false,
      retryAt: null,
      lastRecoveryAction: "Retry accepted",
      lastRecoveryAt: new Date().toISOString(),
    });
    renderJobDetail({
      ...activeJobDetail,
      status: data.status || "ACCEPTED",
      message: "operator retry queued by buyer-demo-operator",
      deadLettered: false,
      retryAt: null,
    });
    setStatus("Operator retry accepted. Tracking conversion status...");
    void pollJob(jobId, statusUrl);
  } catch (err) {
    setError("Network error while requesting retry. Retry when the service is reachable.");
  } finally {
    el.retryJobBtn.replaceChildren(...initialChildren);
    el.retryJobBtn.removeAttribute("aria-busy");
    el.retryJobBtn.disabled = false;
  }
}

function renderSessionKpiFallback(history = loadHistory()) {
  const ready = history.filter(job => job.status === "SUCCEEDED").length;
  const total = history.length;

  el.kpiTotal.textContent = String(total);
  el.kpiReady.textContent = String(ready);
  el.kpiSuccessRate.textContent = total > 0 ? formatPercent(ready / total) : "0%";
  el.kpiP95.textContent = "n/a";
}

function renderKpiSnapshot(snapshot) {
  el.kpiTotal.textContent = String(snapshot.totalJobs ?? 0);
  el.kpiReady.textContent = String(snapshot.succeededJobs ?? 0);
  el.kpiSuccessRate.textContent = formatPercent(snapshot.conversionSuccessRate);
  el.kpiP95.textContent = formatMilliseconds(snapshot.p95TimeToPreviewMs);
}

function renderKpiEvidence(exports) {
  const snapshots = Array.isArray(exports) ? exports : [];
  const latest = snapshots.length > 0 ? snapshots[snapshots.length - 1] : null;

  el.kpiExportCount.textContent = String(snapshots.length);
  el.kpiExportLatest.textContent = latest ? formatTimestamp(latest.exportedAt) : "n/a";
  el.kpiExportSubject.textContent = latest ? formatDetailValue(latest.subjectId) : "n/a";
  el.kpiExportJobs.textContent = latest ? String(latest.totalJobs ?? 0) : "0";
  el.kpiExportStatus.textContent = latest
    ? "Latest evidence reflects an authorized tenant-scoped KPI snapshot export."
    : "No exported KPI snapshots for this tenant yet.";
}

function formatPercent(value) {
  const rate = Number(value);
  return Number.isFinite(rate) ? `${Math.round(rate * 100)}%` : "0%";
}

function formatMilliseconds(value) {
  if (value === null || value === undefined) {
    return "n/a";
  }

  const milliseconds = Number(value);
  return Number.isFinite(milliseconds) ? `${Math.round(milliseconds)} ms` : "n/a";
}

function formatTimestamp(value) {
  if (!value) {
    return "n/a";
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "n/a" : date.toLocaleString();
}

async function refreshKpis() {
  try {
    const { res, data } = await fetchJson(KPI_ENDPOINT);
    if (!res.ok || !data) {
      renderSessionKpiFallback();
      return;
    }

    renderKpiSnapshot(data);
    void refreshKpiEvidence();
  } catch (err) {
    renderSessionKpiFallback();
  }
}

async function refreshKpiEvidence() {
  const initialChildren = Array.from(el.refreshEvidenceBtn.childNodes);
  el.refreshEvidenceBtn.disabled = true;
  el.refreshEvidenceBtn.setAttribute("aria-busy", "true");
  el.refreshEvidenceBtn.textContent = "Refreshing...";

  try {
    const { res, data } = await fetchJson(KPI_EXPORTS_ENDPOINT);
    if (!res.ok) {
      el.kpiExportStatus.textContent = "Snapshot evidence is unavailable for the current tenant claim.";
      return;
    }

    renderKpiEvidence(data);
  } catch (err) {
    el.kpiExportStatus.textContent = "Snapshot evidence is unavailable while the service is unreachable.";
  } finally {
    el.refreshEvidenceBtn.replaceChildren(...initialChildren);
    el.refreshEvidenceBtn.removeAttribute("aria-busy");
    el.refreshEvidenceBtn.disabled = false;
  }
}

async function loadDemoData() {
  const initialChildren = Array.from(el.loadDemoDataBtn.childNodes);
  el.loadDemoDataBtn.disabled = true;
  el.loadDemoDataBtn.setAttribute("aria-busy", "true");
  el.loadDemoDataBtn.textContent = "Loading...";
  setStatus("Loading seeded buyer-demo story...");

  try {
    const { res, data } = await fetchJson(DEMO_FIXTURE_URL);
    if (!res.ok || !data || !Array.isArray(data.history)) {
      setError("Seeded demo story is unavailable.");
      return;
    }

    const demoHistory = data.history.slice(0, 12);
    saveHistory(demoHistory);
    renderHistory(demoHistory);
    if (data.kpiSnapshot) {
      renderKpiSnapshot(data.kpiSnapshot);
    }
    if (Array.isArray(data.kpiExports)) {
      renderKpiEvidence(data.kpiExports);
    }
    setStatus("Seeded buyer-demo story loaded for screenshot and Figma review.");
  } catch (err) {
    setError("Unable to load seeded demo story.");
  } finally {
    el.loadDemoDataBtn.replaceChildren(...initialChildren);
    el.loadDemoDataBtn.removeAttribute("aria-busy");
    el.loadDemoDataBtn.disabled = false;
  }
}

async function fetchJson(url) {
  const res = await fetch(url, {
    headers: jsonHeaders(),
    credentials: "same-origin",
  });
  const contentType = (res.headers.get("content-type") || "").toLowerCase();
  const data = contentType.includes("application/json") ? await res.json() : null;
  return { res, data };
}

async function pollJob(jobId, statusUrl) {
  const { res, data } = await fetchJson(statusUrl);
  if (!res.ok || !data) {
    updateJob(jobId, { status: "FAILED" });
    return;
  }

  const status = data.status || "SUBMITTED";
  updateJob(jobId, {
    status,
    attemptCount: data.attemptCount,
    maxAttempts: data.maxAttempts,
    retryAt: data.retryAt,
    deadLettered: Boolean(data.deadLettered),
    message: data.message,
  });

  if (ACTIVE_STATUSES.has(status)) {
    window.setTimeout(() => {
      void pollJob(jobId, statusUrl);
    }, POLL_DELAY_MS);
  }
}

async function submitDocument(event) {
  event.preventDefault();
  const file = el.fileInput.files && el.fileInput.files[0];
  if (!file) {
    setError("Choose a document before submitting.");
    return;
  }

  const initialChildren = Array.from(el.submitBtn.childNodes);
  el.submitBtn.disabled = true;
  el.submitBtn.setAttribute("aria-busy", "true");
  el.submitBtn.textContent = "Submitting...";
  setStatus("Submitting document...");

  try {
    const body = new FormData();
    body.append("file", file);

    const res = await fetch("/api/v1/convert/jobs", {
      method: "POST",
      body,
      credentials: "same-origin",
      headers: jsonHeaders(),
    });
    const data = (res.headers.get("content-type") || "").includes("application/json") ? await res.json() : null;

    if (!res.ok || !data) {
      const code = data && data.errorCode ? data.errorCode : "FAILED";
      const message = code === "UNSUPPORTED_FORMAT"
        ? "Unsupported format blocked. Use an approved conversion policy override or upload a supported file."
        : (data && data.message) || "Upload failed. Check the document and retry.";
      addFailedHistory(file.name, code);
      setError(message);
      return;
    }

    const job = {
      jobId: data.jobId,
      fileName: file.name,
      status: data.status || "ACCEPTED",
      statusUrl: data.statusUrl,
      submittedAt: new Date().toISOString(),
    };
    const history = [job, ...loadHistory()];
    saveHistory(history);
    renderHistory(history);
    void refreshKpis();
    setStatus("Submitted. Tracking conversion status...");
    void pollJob(job.jobId, job.statusUrl);
    el.form.reset();
  } catch (err) {
    addFailedHistory(file.name, "FAILED");
    setError("Network error while submitting. Retry when the service is reachable.");
  } finally {
    el.submitBtn.replaceChildren(...initialChildren);
    el.submitBtn.removeAttribute("aria-busy");
    el.submitBtn.disabled = false;
  }
}

function addFailedHistory(fileName, status) {
  const history = [{
    fileName,
    status,
    submittedAt: new Date().toISOString(),
  }, ...loadHistory()];
  saveHistory(history);
  renderHistory(history);
  void refreshKpis();
}

function init() {
  renderHistory();
  void refreshKpis();
  for (const job of loadHistory()) {
    if (job.jobId && job.statusUrl && ACTIVE_STATUSES.has(job.status)) {
      void pollJob(job.jobId, job.statusUrl);
    }
  }

  el.form.addEventListener("submit", submitDocument);
  el.loadDemoDataBtn.addEventListener("click", () => {
    void loadDemoData();
  });
  el.clearHistoryBtn.addEventListener("click", () => {
    if (!window.confirm("Are you sure you want to clear all session history?")) {
      return;
    }
    saveHistory([]);
    renderHistory([]);
    activeJobDetail = null;
    el.jobDetail.hidden = true;
    void refreshKpis();
    void refreshKpiEvidence();
    setStatus("Session history cleared.");
  });
  el.retryJobBtn.addEventListener("click", () => {
    void retryActiveJob();
  });
  el.refreshEvidenceBtn.addEventListener("click", () => {
    void refreshKpiEvidence();
  });
}

init();
