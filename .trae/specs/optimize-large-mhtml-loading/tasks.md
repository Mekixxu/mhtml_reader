# Tasks
- [x] Task 1: Update WebView Configuration
  - [x] SubTask 1.1: Modify `core/reader/web/WebViewConfigurator.kt` to remove `settings.cacheMode = WebSettings.LOAD_NO_CACHE` or set it to `LOAD_DEFAULT`.

- [x] Task 2: Update Resource Blocking
  - [x] SubTask 2.1: Modify `core/reader/web/BlockingResourceWebViewClient.kt` to add `url.startsWith("cid:")` to the allowed list for MHTML/HTML files.

- [x] Task 3: Optimize ReaderFragment Loading
  - [x] SubTask 3.1: In `ReaderFragment.loadSelectedWebTab`, checking if `webPreview.url` (or a tracked current path) matches the target file path.
  - [x] SubTask 3.2: If it matches, skip `loadUrl` and `WebViewConfigurator` re-configuration, just ensure visibility and tracker resumption.
  - [x] SubTask 3.3: Ensure `WebViewClient` is not re-set unnecessarily if staying on same content type.

# Task Dependencies
- Task 3 depends on Task 1 and 2 for full effect, but can be done in parallel.
