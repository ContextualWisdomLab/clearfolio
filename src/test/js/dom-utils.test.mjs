import test from 'node:test';
import assert from 'node:assert/strict';
import { setBusyState, createActionButton, createLink } from '../../main/resources/static/assets/viewer/dom-utils.js';

// Mock DOM
global.document = {
  createElement(tag) {
    return new MockElement(tag);
  }
};

class MockElement {
  constructor(tag) {
    this.tagName = tag.toUpperCase();
    this.attributes = {};
    this.childNodes = [];
    this._textContent = "";
    this.disabled = false;
    this.className = "";
    this.type = "";
    this.href = "";
    this.target = "";
    this.rel = "";
    this._listeners = {};
  }

  hasAttribute(name) {
    return name in this.attributes;
  }

  getAttribute(name) {
    return this.attributes[name] !== undefined ? this.attributes[name] : null;
  }

  setAttribute(name, value) {
    this.attributes[name] = value;
  }

  removeAttribute(name) {
    delete this.attributes[name];
  }

  get textContent() {
    return this._textContent;
  }

  set textContent(val) {
    this._textContent = val;
    this.childNodes = [{ type: 'text', value: val }];
  }

  replaceChildren(...nodes) {
    this.childNodes = nodes;
    this._textContent = nodes.filter(n => n.type === 'text').map(n => n.value).join('');
  }

  addEventListener(event, handler) {
    if (!this._listeners[event]) this._listeners[event] = [];
    this._listeners[event].push(handler);
  }

  appendChild(node) {
    this.childNodes.push(node);
  }
}

test('setBusyState sets aria-busy, textContent, and disabled state', () => {
  const btn = new MockElement('button');
  btn.textContent = 'Submit';
  btn.childNodes = [{type: 'icon', name: 'svg'}, {type: 'text', value: 'Submit'}];

  const restore = setBusyState(btn, 'Loading...');

  assert.equal(btn.getAttribute('aria-busy'), 'true');
  assert.equal(btn.textContent, 'Loading...');
  assert.equal(btn.disabled, true);

  restore();

  assert.equal(btn.getAttribute('aria-busy'), null);
  assert.equal(btn.disabled, false);
  assert.equal(btn.childNodes.length, 2);
  assert.equal(btn.childNodes[0].name, 'svg');
});

test('setBusyState handles nested/repeated calls idempotently', () => {
  const btn = new MockElement('button');
  btn.textContent = 'Submit';

  const restore1 = setBusyState(btn, 'Loading...');
  const restore2 = setBusyState(btn, 'Still Loading...');

  assert.equal(btn.getAttribute('aria-busy'), 'true');

  restore2();

  // Should still be busy
  assert.equal(btn.getAttribute('aria-busy'), 'true');
  assert.equal(btn.disabled, true);

  restore1();

  assert.equal(btn.getAttribute('aria-busy'), null);
  assert.equal(btn.disabled, false);
  assert.equal(btn.textContent, 'Submit');
});

test('setBusyState handles duplicate restores safely', () => {
  const btn = new MockElement('button');
  btn.textContent = 'Submit';

  const restore1 = setBusyState(btn, 'Loading...');

  restore1();
  restore1(); // Double restore should not throw and should not decrement depth further

  assert.equal(btn.getAttribute('aria-busy'), null);
});

test('setBusyState preserves pre-existing aria-label and operation-specific names', () => {
  const btn = new MockElement('button');
  btn.textContent = 'Details';
  btn.setAttribute('aria-label', 'View details for file.pdf');

  const restore = setBusyState(btn, 'Loading...');

  assert.equal(btn.getAttribute('aria-label'), 'Loading... View details for file.pdf');

  restore();

  assert.equal(btn.getAttribute('aria-label'), 'View details for file.pdf');
});

test('createActionButton properly creates button with aria-label', () => {
    let clicked = false;
    const btn = createActionButton('Details', () => clicked = true, 'View details for file.pdf');
    assert.equal(btn.getAttribute('aria-label'), 'View details for file.pdf');
    assert.equal(btn.textContent, 'Details');
    btn._listeners['click'][0]();
    assert.equal(clicked, true);
});

test('createLink properly creates link with aria-label', () => {
    const link = createLink('/path', 'Open', 'Open file.pdf');
    assert.equal(link.getAttribute('aria-label'), 'Open file.pdf');
    assert.equal(link.textContent, 'Open');
    assert.equal(link.href, '/path');
});


test('setBusyState handles an initially disabled control', () => {
  const btn = new MockElement('button');
  btn.textContent = 'Submit';
  btn.disabled = true;

  const restore = setBusyState(btn, 'Loading...');
  assert.equal(btn.disabled, true);

  restore();
  assert.equal(btn.disabled, true); // should remain disabled
});

test('setBusyState handles a pre-existing aria-busy value', () => {
  const btn = new MockElement('button');
  btn.textContent = 'Submit';
  btn.setAttribute('aria-busy', 'false');

  const restore = setBusyState(btn, 'Loading...');
  assert.equal(btn.getAttribute('aria-busy'), 'true');

  restore();
  assert.equal(btn.getAttribute('aria-busy'), 'false'); // should restore original
});

test('setBusyState preserves markup-bearing filenames remaining inert text', () => {
  const btn = createActionButton('Details', () => {}, 'View details for <script>alert(1)</script>.pdf');

  const restore = setBusyState(btn, 'Loading...');
  assert.equal(btn.getAttribute('aria-label'), 'Loading... View details for <script>alert(1)</script>.pdf');
  assert.equal(btn.textContent, 'Loading...');

  restore();

  assert.equal(btn.getAttribute('aria-label'), 'View details for <script>alert(1)</script>.pdf');
  assert.equal(btn.textContent, 'Details');
});
