# Fix Permissions and UI Tweaks Spec

## Why
1.  Users cannot browse external storage (`/storage/emulated/0`) because the `MANAGE_EXTERNAL_STORAGE` permission request is missing.
2.  Users lack efficient tab management controls (switch, close, close all) directly from the Tabs list.
3.  New tabs appear at the bottom of the list, which is inconvenient for quickly accessing recently opened files.
4.  The "Favorites" section on the Home page uses a `ListView` style that is inconsistent with the "Recent Opens" button style.

## What Changes

### 1. Fix Permissions (FilesFragment)
-   **Current Behavior**: Accessing local storage only shows app-private directories; no permission prompt appears.
-   **New Behavior**: When accessing local storage, check for `MANAGE_EXTERNAL_STORAGE` permission (Android 11+). If not granted, show a dialog explaining the need for access and direct the user to the system settings page to grant it.
-   **Code Impact**: `FilesFragment.kt` (check and request permission logic).

### 2. Tabs Context Menu (TabsOverviewFragment)
-   **Current Behavior**: Only click to open and a separate close button exist.
-   **New Behavior**: Long-pressing a tab item shows a context menu with:
    -   **Switch**: Switch to this tab (same as click).
    -   **Close**: Close this tab.
    -   **Close All**: Close all open tabs.
-   **Code Impact**: `TabsOverviewFragment.kt` (Adapter, ContextMenu).

### 3. Tabs Sorting (TabsOverviewFragment)
-   **Current Behavior**: Tabs are listed in opening order (oldest at top).
-   **New Behavior**: Tabs are listed in reverse opening order (newest at top).
-   **Code Impact**: `ReaderTabManager.kt` (list retrieval) or `TabsOverviewFragment.kt` (adapter sorting).

### 4. Home Favorites Style (HomeFragment)
-   **Current Behavior**: Favorites are displayed as a `ListView`.
-   **New Behavior**: Favorites are displayed as a prominent button (similar to "RECENT OPENS"), likely navigating to the dedicated `FavoritesFragment`.
-   **Code Impact**: `fragment_home.xml` (replace ListView with Button), `HomeFragment.kt` (click listener).

## Impact
-   **Affected Specs**: `UI_Spec0.png` (Home layout update).
-   **Affected Code**: `FilesFragment.kt`, `TabsOverviewFragment.kt`, `HomeFragment.kt`, `fragment_home.xml`.

## ADDED Requirements
### Requirement: Storage Permission
The system SHALL request `MANAGE_EXTERNAL_STORAGE` permission when the user attempts to browse local storage if it is not already granted.

### Requirement: Tabs Management
The system SHALL provide a context menu on long-press for tab items, offering "Switch", "Close", and "Close All" options.

### Requirement: Tabs Sorting
The system SHALL display the list of open tabs with the most recently opened tab at the top.

## MODIFIED Requirements
### Requirement: Home Favorites Display
The system SHALL display a "Favorites" entry point on the Home screen as a button consistent with the "Recent Opens" button style, replacing the inline list.
