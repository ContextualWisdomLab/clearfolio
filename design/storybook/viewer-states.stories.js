import { expect, userEvent, within } from 'storybook/test';

const DOCUMENT_ID = '00000000-0000-0000-0000-000000000000';

const STATE_COPY = {
  loading: {
    status: 'Preparing the preview. No action is needed yet.',
    preview: 'The document preview will appear here when preparation finishes.',
  },
  ready: {
    status: 'Preview ready. Open the document or download the original.',
    preview: 'Document preview is ready for review.',
  },
  failed: {
    status: 'Preview preparation failed.',
    errorTitle: 'Preview unavailable',
    errorMessage: 'Try the preview again. If it fails again, download the original file.',
    preview: 'The preview could not be prepared.',
  },
  notFound: {
    status: 'Document not found.',
    errorTitle: 'Document unavailable',
    errorMessage: 'Check the link or return to your document list.',
    preview: 'No document is available for this reference.',
  },
  invalid: {
    status: 'This document link is invalid.',
    errorTitle: 'Invalid document link',
    errorMessage: 'Open a valid document link, then try again.',
    preview: 'A valid document link is required before a preview can be shown.',
  },
  network: {
    status: 'The preview service could not be reached.',
    errorTitle: 'Connection interrupted',
    errorMessage: 'Check your connection, then try the preview again.',
    preview: 'The preview is waiting for a working connection.',
  },
};

const HARNESS_STYLES = `
  <style>
    .storybook-viewer-frame {
      width: 100%;
      margin: 0 auto;
    }
    .storybook-viewer-frame .preview-placeholder {
      min-height: 190px;
      display: grid;
      place-items: center;
      text-align: center;
      color: var(--muted);
      padding: 24px;
    }
    .storybook-viewer-frame .story-note {
      margin: 12px 0 0;
      color: var(--muted);
      font-size: 13px;
      overflow-wrap: anywhere;
    }
    @media (prefers-reduced-motion: reduce) {
      .storybook-viewer-frame .skeleton {
        animation: none;
      }
    }
    @media (forced-colors: active) {
      .storybook-viewer-frame .status,
      .storybook-viewer-frame .error,
      .storybook-viewer-frame .preview,
      .storybook-viewer-frame .btn {
        border-color: CanvasText;
      }
      .storybook-viewer-frame .btn-primary {
        forced-color-adjust: auto;
      }
    }
  </style>
`;

function renderViewer({ state = 'ready', busy = false } = {}) {
  const copy = STATE_COPY[state];
  const root = document.createElement('div');
  root.className = 'storybook-viewer-frame';

  const error = copy.errorTitle
    ? `<div class="error" role="alert">
        <p class="error__title">${copy.errorTitle}</p>
        <p class="error__message">${copy.errorMessage}</p>
      </div>`
    : '';
  const skeleton = state === 'loading'
    ? '<div class="skeleton" aria-hidden="true"></div>'
    : `<div class="preview-placeholder" role="document" aria-label="Document preview">${copy.preview}</div>`;
  const unrecoverableReference = state === 'notFound' || state === 'invalid';
  const primaryLabel = state === 'ready'
    ? 'Open document'
    : state === 'notFound'
      ? 'Check document link'
      : state === 'invalid'
        ? 'Open valid document link'
        : 'Try preview again';
  const primaryDisabled = busy ? ' disabled' : '';
  const downloadAction = unrecoverableReference
    ? ''
    : `<button class="btn btn-secondary" type="button"${primaryDisabled}>Download original</button>`;

  root.innerHTML = `${HARNESS_STYLES}
    <main class="app-main" aria-labelledby="viewer-title">
      <h1 class="page-title" id="viewer-title">Clearfolio document viewer</h1>
      <p class="page-subtitle">Review the document state, then choose the next available action.</p>
      <section class="panel" aria-label="Preview workspace" aria-busy="${busy}">
        <div class="panel-header">
          <div>
            <h2 class="panel__title" id="preview-title">Document preview</h2>
            <p class="panel__caption">Reference ${DOCUMENT_ID}</p>
          </div>
        </div>
        <div class="status" role="status" aria-live="polite">${copy.status}</div>
        ${error}
        <div class="preview" role="region" aria-labelledby="preview-title">${skeleton}</div>
        <div class="actions">
          <button class="btn btn-primary" type="button"${primaryDisabled}>${primaryLabel}</button>
          ${downloadAction}
          <button class="btn btn-secondary" type="button">Back to documents</button>
        </div>
        <p class="story-note">Exact document reference: ${DOCUMENT_ID}</p>
      </section>
    </main>`;
  return root;
}

const meta = {
  title: 'Viewer/Buyer states',
  tags: ['test'],
  render: (args) => renderViewer(args),
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;

export const Loading = {
  args: { state: 'loading', busy: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByText(STATE_COPY.loading.preview)).toBeVisible();
  },
};

export const Ready = {
  args: { state: 'ready' },
};

export const Failed = {
  args: { state: 'failed' },
};

export const NotFound = {
  args: { state: 'notFound' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole('button', { name: 'Check document link' })).toBeEnabled();
    await expect(canvas.getByRole('button', { name: 'Back to documents' })).toBeEnabled();
    await expect(canvas.queryByRole('button', { name: 'Try preview again' })).not.toBeInTheDocument();
    await expect(canvas.queryByRole('button', { name: 'Download original' })).not.toBeInTheDocument();
  },
};

export const InvalidDocument = {
  args: { state: 'invalid' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole('button', { name: 'Open valid document link' })).toBeEnabled();
    await expect(canvas.getByRole('button', { name: 'Back to documents' })).toBeEnabled();
    await expect(canvas.queryByRole('button', { name: 'Try preview again' })).not.toBeInTheDocument();
    await expect(canvas.queryByRole('button', { name: 'Download original' })).not.toBeInTheDocument();
  },
};

export const NetworkError = {
  args: { state: 'network' },
};

export const KeyboardFocus = {
  args: { state: 'ready' },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const primary = canvas.getByRole('button', { name: 'Open document' });
    await userEvent.tab();
    await expect(primary).toHaveFocus();
  },
};

export const BusyDisabled = {
  args: { state: 'loading', busy: true },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole('region', { name: 'Preview workspace' })).toHaveAttribute('aria-busy', 'true');
    await expect(canvas.getByRole('button', { name: 'Try preview again' })).toBeDisabled();
    await expect(canvas.getByRole('button', { name: 'Download original' })).toBeDisabled();
    await expect(canvas.getByRole('button', { name: 'Back to documents' })).toBeEnabled();
  },
};

export const MobileLoading = {
  args: { state: 'loading', busy: true },
  globals: {
    viewport: { value: 'mobile1', isRotated: false },
  },
};

export const TabletReady = {
  args: { state: 'ready' },
  globals: {
    viewport: { value: 'tablet', isRotated: false },
  },
};
