# Navigation State Preservation Spec

## Why
When switching between tabs (e.g., from Tabs/Reader back to Folders), the `FilesFragment` is currently destroyed and recreated. This causes the user to lose their scroll position and potentially reset their browsing context if the session wasn't fully persisted. The user expects to return to the exact same state they left.

## What Changes
- Refactor `MainActivity` navigation logic to use `hide`/`show` transactions instead of `replace` for main tab switching.
- Introduce a `switchFragment` helper method that reuses existing fragments by tag if available.
- Update `BottomNavigationView` listener to leverage this new method, ensuring fragment instances are preserved.
- Maintain `replace` behavior for explicit navigation actions (e.g., opening a new directory from Home) where a fresh state is desired.

## Impact
- **User Experience**: Switching tabs feels instant and preserves scroll position/state.
- **Performance**: Reduces overhead of recreating fragments and reloading directory contents from disk/network on every tab switch.
- **Code**: `MainActivity.kt` becomes the central place for fragment transaction logic.

## ADDED Requirements
### Requirement: Preserve Tab State
The system SHALL preserve the state (scroll position, expanded folders, selection) of the `FilesFragment` and `ReaderFragment` when switching between bottom navigation tabs.

#### Scenario: Switch Tabs
- **WHEN** user is browsing a folder in the "Files" tab
- **AND** user switches to "Reader" tab
- **AND** user switches back to "Files" tab
- **THEN** the `FilesFragment` should be in the exact same state (same directory, same scroll position).

## MODIFIED Requirements
### Requirement: Navigation Logic
The `MainActivity` navigation logic SHALL reuse existing fragment instances by tag instead of always replacing them, unless an explicit "open new" action is performed.
