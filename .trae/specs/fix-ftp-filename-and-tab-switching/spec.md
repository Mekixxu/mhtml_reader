# Fix FTP Filename and Tab Switching Spec

## Why
1.  **FTP Filename Issue**: Downloaded FTP files use a generic name like `ftp_TIMESTAMP_HASH.mht`. This confuses the user as it doesn't match the original filename.
2.  **Tab Switching Bug**: Users report being unable to switch tabs; the UI remains stuck on the last opened tab. This suggests the `ReaderViewModel` or `TabsOverviewFragment` logic isn't correctly updating the `selectedTabId` or notifying the `ReaderFragment`.

## What Changes

### FTP Filename Handling (`FilesFragment.kt`)
- **Logic Change**: In `downloadFtpToLocal`, instead of generating a timestamp-based name, sanitize and use the original `displayName`.
- **Conflict Handling**: If a file with the same name exists in the cache, overwrite it (or append a counter if we want to keep history, but overwriting is cleaner for "open" semantics).
- **Naming Convention**: `ftp_open/ORIGINAL_NAME.ext`.

### Tab Switching Fix (`ReaderFragment.kt` & `ReaderViewModel.kt`)
- **Diagnosis**: `ReaderFragment` observes `readerViewModel.tabs`, but it might be resetting `selectedTabId` to the last tab every time the list updates, ignoring the user's switch request.
- **Fix**:
    - `ReaderFragment` should respect the `selectedTabId` if it's valid.
    - `ReaderViewModel` needs to expose a `currentTabId` state or event to explicitly drive navigation.
    - Currently, `ReaderFragment` has local state `selectedTabId`. When `TabsOverviewFragment` calls `viewModel.switchTo(id)`, it updates `tabManager`.
    - `ReaderTabManager` has `switchTo(tabId)` which is empty in `DefaultReaderTabManager`.
    - **Crucial Fix**: Implement `switchTo` in `ReaderTabManager` to emit a "Navigation Event" or update a `currentTabId` StateFlow.
    - Update `ReaderFragment` to observe `currentTabId` from ViewModel.

## Impact
- **UX**: FTP files show correct names in tabs.
- **Navigation**: Tab switching works reliably.

## Implementation Details

### 1. ReaderTabManager
- Add `currentTabId: StateFlow<String?>` to interface.
- Implement in `DefaultReaderTabManager`.
- Update `switchTo` to emit new value.
- Update `openNewTab` to auto-switch to new tab.

### 2. ReaderViewModel
- Expose `currentTabId`.

### 3. ReaderFragment
- Observe `viewModel.currentTabId`.
- Remove local `selectedTabId` logic that overrides selection.

### 4. FilesFragment
- Update `downloadFtpToLocal` and `downloadSmbToLocal` to use `displayName` directly (sanitized).
