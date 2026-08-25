export class MockTextNode {
  constructor(text) {
    this.type = "text";
    this.textContent = String(text);
  }
}

export class MockElement {
  constructor(tagName = "div") {
    this.tagName = tagName.toUpperCase();
    this.attributes = new Map();
    this.childNodes = [];
    this.listeners = new Map();
    this.disabled = false;
    this.hidden = false;
    this.className = "";
    this.type = "";
    this.href = "";
    this.target = "";
    this.rel = "";
    this.files = [];
  }

  get textContent() {
    return this.childNodes.map(node => node.textContent).join("");
  }

  set textContent(value) {
    const text = String(value);
    this.childNodes = text === "" ? [] : [new MockTextNode(text)];
  }

  appendChild(node) {
    if (node && node.nodeType === 11) {
      this.childNodes.push(...node.childNodes);
      node.childNodes = [];
    } else {
      this.childNodes.push(node);
    }
    return node;
  }

  append(...nodes) {
    for (const node of nodes) {
      if (node && node.nodeType === 11) {
        this.childNodes.push(...node.childNodes);
        node.childNodes = [];
      } else {
        this.childNodes.push(node);
      }
    }
  }

  replaceChildren(...nodes) {
    this.childNodes = nodes;
  }

  addEventListener(type, listener) {
    this.listeners.set(type, listener);
  }

  dispatchEvent(event) {
    if (this.disabled) {
      return false;
    }
    event.currentTarget = this;
    const listener = this.listeners.get(event.type);
    if (listener !== undefined) {
      listener(event);
    }
    return true;
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
  }

  getAttribute(name) {
    return this.attributes.has(name) ? this.attributes.get(name) : null;
  }

  removeAttribute(name) {
    this.attributes.delete(name);
  }

  focus() {}

  reset() {}
}

export class MockDocumentFragment {
  constructor() {
    this.childNodes = [];
    this.nodeType = 11;
  }
  appendChild(node) {
    this.childNodes.push(node);
    return node;
  }
  append(...nodes) {
    this.childNodes.push(...nodes);
  }
}
