# Fix Tabs/Folders Navigation Toggle Spec

## Why
Users report that clicking the "Tabs" or "Folders" bottom navigation item repeatedly does not toggle between the "Overview" page and the "Content" (Reader/Files) page as expected. Specifically:
1.  In Tabs mode: Clicking "Tabs" switches to the list, but clicking again doesn't switch back to the open file.
2.  In Folders mode: Clicking "Folders" switches to the list, but clicking again doesn't switch back to the open directory.

## What Changes
- **Fix `setOnItemReselectedListener` Logic**: The current logic in `MainActivity.kt` creates NEW instances of fragments (`FilesFragment`, `ReaderFragment`) instead of finding and showing existing ones when toggling back from Overview.
- **Use `findFragmentByTag`**: Instead of `showContent(FilesFragment(), ...)` which might create a new one or replace it, we should explicitly find the existing fragment by tag and show it.
- **Correct Toggle Logic**:
    - If current is Overview -> Switch to Content (if exists).
    - If current is Content -> Switch to Overview.

## Impact
- **Affected specs**: Navigation behavior for Tabs and Folders.
- **Affected code**: `app/src/main/java/html_reader/MainActivity.kt`

## ADDED Requirements
### Requirement: Navigation Toggle
The system SHALL toggle between the Overview page and the last active Content page when the user re-selects the current bottom navigation item.

#### Scenario: Toggle Tabs
- **GIVEN** user is reading a file (ReaderFragment).
- **WHEN** user clicks "Tabs" button.
- **THEN** show TabsOverviewFragment.
- **WHEN** user clicks "Tabs" button again.
- **THEN** show ReaderFragment (restore state).

#### Scenario: Toggle Folders
- **GIVEN** user is browsing a directory (FilesFragment).
- **WHEN** user clicks "Folders" button.
- **THEN** show FoldersOverviewFragment.
- **WHEN** user clicks "Folders" button again.
- **THEN** show FilesFragment (restore state).

## MODIFIED Requirements
### Requirement: `setOnItemReselectedListener` Implementation
**Old**: Creates new fragments or fails to find existing ones due to incorrect logic.
**New**: Explicitly looks up fragment by tag (`reader_mode`, `directory_mode_folders`) and shows it if it exists; otherwise falls back to Overview or stays put.
