# Refactor Tabs UI and Actions Spec

## Why
The user finds the top buttons (Switch, Close, Close All) in the Tabs Overview page redundant as these actions are available (or should be) in the context menu. Additionally, users want to be able to add a tab directly to favorites from this list.

## What Changes
- **Remove Top Buttons**: Remove `switch`, `close`, and `close_all` buttons from `fragment_tabs_overview.xml` and `TabsOverviewFragment.kt`.
- **Enhance Context Menu**: Add "Add to Favorites" option to the long-press context menu of tab items.
- **Implement Favorite Logic**: When "Add to Favorites" is selected, use `FavoritesRepository` to add the tab's path/url to favorites.

## Impact
- **Affected specs**: Tabs UI layout, Tabs context menu actions.
- **Affected code**:
  - `app/src/main/res/layout/fragment_tabs_overview.xml`
  - `app/src/main/java/html_reader/TabsOverviewFragment.kt`

## ADDED Requirements
### Requirement: Add to Favorites from Tabs
The system SHALL provide an option to add an open tab to favorites via the context menu.
#### Scenario: User adds favorite
- **WHEN** user long-presses a tab item in Tabs Overview.
- **THEN** a context menu appears with "Add to Favorites".
- **WHEN** user selects "Add to Favorites".
- **THEN** the file/url is added to the Favorites database and a confirmation Toast is shown.

## REMOVED Requirements
### Requirement: Top Action Buttons
**Reason**: Redundant with context menu and takes up screen space.
**Migration**: Actions are already available in context menu.

## MODIFIED Requirements
### Requirement: Tabs Context Menu
**Old**: Switch, Close, Close All.
**New**: Switch, Close, Close All, **Add to Favorites**.
