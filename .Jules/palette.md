## 2024-06-30 - Added Loading Spinner for Refresh Button
**Learning:** Refresh buttons in async processes often need a clear loading indicator, such as a spinner and pointer events disabled, as text changes alone ("Refreshing...") may be overlooked. Disabling pointer-events helps prevent multi-clicks while the disabled attribute acts as a secondary layer.
**Action:** Always complement text changes on async buttons with visual indicators (like a spinner and opacity change) to ensure the user perceives the busy state immediately.
