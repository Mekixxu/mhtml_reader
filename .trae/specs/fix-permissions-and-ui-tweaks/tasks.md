# Tasks

- [x] Task 1: Fix Storage Permissions in FilesFragment
  - [x] SubTask 1.1: Add `MANAGE_EXTERNAL_STORAGE` permission check in `FilesFragment`.
  - [x] SubTask 1.2: Implement dialog to explain and request permission, navigating to `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`.
  - [x] SubTask 1.3: Handle permission result (refresh file list if granted).

- [x] Task 2: Implement Tabs Context Menu
  - [x] SubTask 2.1: Register context menu for `TabsOverviewFragment` ListView.
  - [x] SubTask 2.2: Implement `onCreateContextMenu` with "Switch", "Close", and "Close All" options.
  - [x] SubTask 2.3: Handle context menu clicks in `onContextItemSelected`.

- [x] Task 3: Reverse Tabs Sorting
  - [x] SubTask 3.1: Reverse the order of tabs in `ReaderTabManager` or `TabsOverviewFragment` adapter (newest at index 0).

- [x] Task 4: Update Home Favorites UI
  - [x] SubTask 4.1: Modify `fragment_home.xml` to remove `favorites_list` and replace it with a `Button` (e.g., `btn_favorites`) matching `btn_recent_opens` style.
  - [x] SubTask 4.2: Update `HomeFragment.kt` to remove ListView setup and handle Button click (navigate to `FavoritesFragment`).

# Task Dependencies
- Task 4 (UI update) requires `FavoritesFragment` to exist (which it does).
