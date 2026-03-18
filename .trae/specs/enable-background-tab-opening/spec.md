# Enable Background Tab Opening Spec

## Why
The user wants to open files in new tabs from the "Folders" page without automatically switching to the "Reader" page. This allows them to open multiple files in succession while remaining in the file browser. Currently, opening a file always navigates to the Reader view.

## What Changes
- **Core Model**: Add `background` flag to `OpenRequest`.
- **Reader Logic**: Update `DefaultReaderTabManager` to respect the `background` flag. If true, the new tab is created but not set as the current active tab.
- **UI Logic**: Update `FilesFragment` to support background opening:
  - Modify "Open in new tab" action to use `background = true`.
  - Handle the opening process (download + open) without triggering a navigation to `ReaderFragment`.
  - Show status feedback (Toast) instead of navigation.

## Impact
- **Affected specs**: `FilesFragment` context menu behavior, `ReaderTabManager` tab switching logic.
- **Affected code**:
  - `core/reader/model/OpenRequest.kt`
  - `core/reader/tab/DefaultReaderTabManager.kt`
  - `app/src/main/java/html_reader/FilesFragment.kt`

## ADDED Requirements
### Requirement: Background Tab Opening
The system SHALL support opening a file in a new tab without making it the active tab.
#### Scenario: Open in New Tab (Background)
- **WHEN** user selects "Open in new tab" from the context menu in Files view.
- **THEN** the file is downloaded (if network) and opened in a new Reader tab.
- **THEN** the application remains on the Files view.
- **THEN** a notification (Toast) confirms the file has been opened in the background.

## MODIFIED Requirements
### Requirement: Reader Tab Management
**Old**: `openNewTab` always sets the new tab as `currentTabId`.
**New**: `openNewTab` sets `currentTabId` ONLY if `OpenRequest.background` is `false`.

### Requirement: Files Fragment Context Menu
**Old**: "Open in new tab" calls `showReaderModeWithPath` (navigates).
**New**: "Open in new tab" triggers a background open flow.
