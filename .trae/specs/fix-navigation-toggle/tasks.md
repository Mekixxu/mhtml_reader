# Tasks
- [ ] Task 1: Fix `setOnItemReselectedListener` in `MainActivity.kt`
  - [ ] SubTask 1.1: Update `R.id.nav_files` case:
    - Check if current fragment is `FoldersOverviewFragment` (by tag or instance).
    - If yes, find fragment with tag `directory_mode_folders`.
    - If found, show it using `switchFragment` (no create).
    - If not found (no directory open), stay on Overview or show empty state (current behavior is fine, just ensure we don't create a blank one unnecessarily).
    - If current is NOT `FoldersOverviewFragment`, show `FoldersOverviewFragment`.
  - [ ] SubTask 1.2: Update `R.id.nav_reader` case:
    - Check if current fragment is `TabsOverviewFragment`.
    - If yes, find fragment with tag `reader_mode`.
    - If found, show it.
    - If current is NOT `TabsOverviewFragment`, show `TabsOverviewFragment`.

# Task Dependencies
- None
