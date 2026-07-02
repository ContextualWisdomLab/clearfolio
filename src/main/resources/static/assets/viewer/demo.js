const STORAGE_KEY = "clearfolio-demo-history-v1";
const POLL_DELAY_MS = 1500;
const ACTIVE_STATUSES = new Set(["ACCEPTED", "SUBMITTED", "PROCESSING"]);

const el = {
  form: document.getElementById("upload-form"),
  fileInput: document.getElementById("file-input"),
  submitBtn: document.getElementById("submit-btn"),
  status: document.getElementById("demo-status"),
  error: document.getElementById("demo-error"),
  errorMessage: document.getElementById("demo-error-message"),
  errorTitle: document.getElementById("demo-error-title"),
  historyBody: document.getElementById("history-body"),
  emptyHistory: document.getElementById("empty-history"),
  clearHistoryBtn: document.getElementById("clear-history-btn"),
  kpiSubmitted: document.getElementById("kpi-submitted"),
  kpiReady: document.getElementById("kpi-ready"),
  kpiAction: document.getElementById("kpi-action"),
};

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

function updateJob(jobId, patch) {
  const history = loadHistory();
  const next = history.map(job => (job.jobId === jobId ? { ...job, ...patch } : job));
  saveHistory(next);
  renderHistory(next);
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

function renderHistory(history = loadHistory()) {
  el.historyBody.textContent = "";
  el.emptyHistory.hidden = history.length > 0;

  let ready = 0;
  let action = 0;

  for (const job of history) {
    if (job.status === "SUCCEEDED") {
      ready++;
    }
    if (job.status === "FAILED" || job.status === "UNSUPPORTED_FORMAT" || job.status === "BAD_REQUEST") {
      action++;
    }

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
      actionsCell.appendChild(createLink(job.statusUrl, "Status JSON"));
    }
    if (job.jobId) {
      actionsCell.appendChild(createLink(`/viewer/${encodeURIComponent(job.jobId)}`, "Open viewer"));
    }

    row.append(fileCell, statusCell, submittedCell, actionsCell);
    el.historyBody.appendChild(row);
  }

  el.kpiSubmitted.textContent = String(history.length);
  el.kpiReady.textContent = String(ready);
  el.kpiAction.textContent = String(action);
}

async function fetchJson(url) {
  const res = await fetch(url, {
    headers: { Accept: "application/json" },
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
  updateJob(jobId, { status });

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

  el.submitBtn.disabled = true;
  setStatus("Submitting document...");

  try {
    const body = new FormData();
    body.append("file", file);

    const res = await fetch("/api/v1/convert/jobs", {
      method: "POST",
      body,
      credentials: "same-origin",
      headers: { Accept: "application/json" },
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
      submittedAt: new Date().toLocaleString(),
    };
    const history = [job, ...loadHistory()];
    saveHistory(history);
    renderHistory(history);
    setStatus("Submitted. Tracking conversion status...");
    void pollJob(job.jobId, job.statusUrl);
    el.form.reset();
  } catch (err) {
    addFailedHistory(file.name, "FAILED");
    setError("Network error while submitting. Retry when the service is reachable.");
  } finally {
    el.submitBtn.disabled = false;
  }
}

function addFailedHistory(fileName, status) {
  const history = [{
    fileName,
    status,
    submittedAt: new Date().toLocaleString(),
  }, ...loadHistory()];
  saveHistory(history);
  renderHistory(history);
}

function init() {
  renderHistory();
  for (const job of loadHistory()) {
    if (job.jobId && job.statusUrl && ACTIVE_STATUSES.has(job.status)) {
      void pollJob(job.jobId, job.statusUrl);
    }
  }

  el.form.addEventListener("submit", submitDocument);
  el.clearHistoryBtn.addEventListener("click", () => {
    saveHistory([]);
    renderHistory([]);
    setStatus("Session history cleared.");
  });
}

init();
