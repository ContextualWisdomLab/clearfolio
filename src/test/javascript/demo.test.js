const { test, describe } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

describe('demo.js DOM state transitions', () => {
    test('clearHistoryBtn disabled state synchronized with history length', () => {
        // Create an isolated context to execute the JS
        const vm = require('node:vm');
        const context = {
            window: {},
            console: console,
            URL: URL,
            Promise: Promise,
            Array: Array,
            Date: Date,
            Set: Set,
            Math: Math,
            Number: Number,
            String: String,
            Boolean: Boolean,
            JSON: JSON,
            encodeURIComponent: encodeURIComponent,
            setTimeout: setTimeout,
        };

        // Mock document elements
        const elements = {};

        context.document = {
            getElementById: (id) => {
                if (!elements[id]) {
                    elements[id] = {
                        id: id,
                        hidden: false,
                        disabled: false,
                        textContent: '',
                        focus: () => {},
                        appendChild: () => {},
                        addEventListener: () => {},
                        files: [],
                        reset: () => {},
                        setAttribute: () => {},
                        getAttribute: () => null,
                        replaceChildren: () => {},
                        childNodes: []
                    };
                }
                return elements[id];
            },
            createElement: (tag) => {
                return {
                    tagName: tag,
                    hidden: false,
                    disabled: false,
                    textContent: '',
                    className: '',
                    appendChild: () => {},
                    addEventListener: () => {},
                    setAttribute: () => {},
                    getAttribute: () => null,
                    replaceChildren: () => {},
                    childNodes: [],
                    append: () => {}
                };
            }
        };

        // Mock localStorage
        context.localStorage = {
            store: {},
            getItem: function(key) { return this.store[key] || null; },
            setItem: function(key, value) { this.store[key] = value.toString(); }
        };

        // Mock fetch
        context.fetch = () => Promise.resolve({ ok: true, json: () => Promise.resolve({}) });

        vm.createContext(context);

        // Read demo.js
        const scriptContent = fs.readFileSync(path.resolve(__dirname, '../../../src/main/resources/static/assets/viewer/demo.js'), 'utf8');

        // Remove import and init
        const cleanScript = scriptContent
            .replace(/import \{.*\} from "\.\/dom-utils\.js";/g, '')
            .replace(/init\(\);/g, '')
            .replace(/const el = /g, 'globalThis.el = '); // Make el available in globalThis

        // Provide the missing imported functions inside the VM script
        const domUtilsMock = `
            function setBusyState(button, text) { return () => {}; }
            function createActionButton(label, click) {
                const btn = document.createElement('button');
                btn.textContent = label;
                return btn;
            }
            function createLink(href, label) {
                const a = document.createElement('a');
                a.href = href;
                a.textContent = label;
                return a;
            }
        `;

        const fullScript = domUtilsMock + "\n" + cleanScript + "\n" + "globalThis.renderHistory = renderHistory;";

        vm.runInContext(fullScript, context);

        // Extract required objects from context
        const renderHistory = context.renderHistory;
        const el = context.el;

        // Test actual state transitions

        // 1. Initial empty history
        renderHistory([]);
        assert.strictEqual(el.clearHistoryBtn.disabled, true);

        // 2. Loading one entry enables the control
        renderHistory([{jobId: '123', status: 'SUCCEEDED'}]);
        assert.strictEqual(el.clearHistoryBtn.disabled, false);

        // 3. Clearing the final entry disables it again
        renderHistory([]);
        assert.strictEqual(el.clearHistoryBtn.disabled, true);

        // 4. Repeated render calls with same state
        renderHistory([]);
        assert.strictEqual(el.clearHistoryBtn.disabled, true);
    });
});
