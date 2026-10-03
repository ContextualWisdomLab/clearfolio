Plan:
1. Update `src/test/js/mock-dom.mjs`:
   Add `MockDocumentFragment` class.
   Update `MockElement`'s `appendChild` and `append` methods to handle `MockDocumentFragment` correctly, unrolling its `childNodes`.
2. Update `src/test/js/demo-integration.test.mjs` and `src/test/js/dom-utils.test.mjs`:
   Add `createDocumentFragment() { return new MockDocumentFragment(); }` to `globalThis.document`.
3. Update `src/main/resources/static/assets/viewer/demo.js`:
   Change `addDetailRow`'s signature to `addDetailRow(fragment, label, value)` and use `fragment.append(term, description)`.
   Update `renderJobDetail` to create a `DocumentFragment`, pass it to `addDetailRow`, and append it to `el.jobDetailBody`.
4. Run `node --test src/test/js/*.test.mjs` to ensure tests pass.
5. Add learning entry to `.jules/bolt.md` about using `DocumentFragment` for DOM batching in frontend rendering loops.
6. Check with `pre_commit_instructions`.
