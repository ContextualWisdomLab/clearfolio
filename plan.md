1. **Analyze UI for UX improvements:** Check the upload button (`#submit-btn`). When `submitDocument` in `demo.js` is called, `el.submitBtn.disabled = true;` is executed and `setStatus("Submitting document...")` is called. The button text `Submit document` does not change to indicate loading state directly within the button itself, only the status div changes.
   Also check `retryJobBtn` in `demo.js`.
2. **Proposed UX Improvement:** Add visual feedback directly to the button during the loading state. Modify `submitBtn.textContent` to indicate loading ("Submitting..."), and optionally add a loading state indication in `retryActiveJob()` for the `retryJobBtn`.
   - Update `src/main/resources/static/assets/viewer/demo.js`:
     - Inside `submitDocument`:
       - Save `const originalText = el.submitBtn.textContent;`
       - Update `el.submitBtn.textContent = "Submitting...";`
       - Add `aria-busy` logic if needed. Or just change text content.
       - In `finally`, restore `el.submitBtn.textContent = "Submit document";`
     - Inside `retryActiveJob`:
       - Update `el.retryJobBtn.textContent = "Requesting retry...";`
       - In `finally`, restore `el.retryJobBtn.textContent = "Retry dead-lettered job";`
3. **Verify:** Run a test checking if the submission button disables and changes the text. Test visual verification using playwright. Run tests and checkstyle checks.
4. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
5. **Submit.**
