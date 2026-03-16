# Tasks
- [ ] Task 1: Update Layout
  - [ ] SubTask 1.1: Remove the top `LinearLayout` containing `tabs_switch`, `tabs_close`, and `tabs_close_all` buttons from `app/src/main/res/layout/fragment_tabs_overview.xml`.

- [ ] Task 2: Update TabsOverviewFragment
  - [ ] SubTask 2.1: Remove button member variables and their initialization/listeners in `onViewCreated`.
  - [ ] SubTask 2.2: Inject `FavoritesRepository` using `FilesRuntime.favoritesRepository(requireContext())` (or ensure access to it).
  - [ ] SubTask 2.3: Update `onCreateContextMenu` to add "Add to Favorites" item (e.g., id 4).
  - [ ] SubTask 2.4: Update `onContextItemSelected` to handle id 4:
    - Get `FavoritesRepository`.
    - Determine `SourceType` (LOCAL/SMB/FTP) from tab data or path (Tabs might need to store source type more explicitly or we infer it). *Correction*: `ReaderTab` has `sourcePathRaw` and `fileType`. We might need to infer `SourceType` from the path string or check if we can get it easily. For v1, inferring from path prefix (`smb://`, `ftp://`) is acceptable as per existing logic.
    - Call `favoritesRepository.addFile`.
    - Show Toast.

# Task Dependencies
- None
