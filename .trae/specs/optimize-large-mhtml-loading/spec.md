# Optimize Large MHTML Loading Spec

## Why
Users report significant lag when loading large MHTML files (>200MB) and when switching tabs back to these files. This is caused by:
1.  **Full Reload**: Every tab switch triggers `loadUrl`, forcing a full re-parse of the MHTML.
2.  **Resource Blocking**: `BlockingResourceWebViewClient` intercepts MHTML internal resources (like `cid:`), causing rendering issues and overhead.
3.  **Strict Caching**: `WebViewConfigurator` disables cache, preventing reuse of parsed resources.

## What Changes
- **WebView Caching**: Enable default caching in `WebViewConfigurator`.
- **MHTML Resource Support**: Whitelist `cid:` scheme in `BlockingResourceWebViewClient`.
- **Smart Reloading**: In `ReaderFragment`, avoid calling `loadUrl` if the file is already loaded (check current URL).

## Impact
- **Affected specs**: `ReaderFragment` loading logic, `BlockingResourceWebViewClient` interception, `WebViewConfigurator` settings.
- **Affected code**:
  - `core/reader/web/BlockingResourceWebViewClient.kt`
  - `core/reader/web/WebViewConfigurator.kt`
  - `app/src/main/java/html_reader/ReaderFragment.kt`

## ADDED Requirements
### Requirement: Smart Tab Switching
The system SHALL NOT reload the WebView content if the target file is already loaded in the WebView.
#### Scenario: Switching to same tab
- **WHEN** user switches to a tab that is already active or matches the currently loaded URL.
- **THEN** the system skips `loadUrl` and just ensures the WebView is visible.

### Requirement: MHTML Resource Support
The system SHALL allow loading of `cid:` resources for MHTML files.
#### Scenario: Loading MHTML with internal images
- **WHEN** WebView requests a resource with `cid:` scheme.
- **THEN** `BlockingResourceWebViewClient` allows the request to proceed (returns null).

## MODIFIED Requirements
### Requirement: WebView Caching
**Old**: `LOAD_NO_CACHE`
**New**: `LOAD_DEFAULT` (or remove the explicit cache mode setting to use default).

### Requirement: Tab Switching Logic
**Old**: Always `loadUrl` on tab switch.
**New**: Check `webPreview.url` vs target file URL. If match, skip `loadUrl`.
