# Optimize Performance and Refactor UI Spec

## Why
1.  **Performance**: Large MHTML files (100MB+) cause UI freezing, scroll lag, and crashes due to memory pressure and rendering overhead.
2.  **Home UI**: The "Recent Opens" list is cluttered. User requested a cleaner UI with a dedicated button/page.
3.  **Missing Features**: "Rename" and "Delete" options are reportedly missing in Files context menu.
4.  **UX Polish**: Sort order in Folders is lost when switching tabs.

## What Changes

### Performance Optimization
- **Manifest**: Add `android:largeHeap="true"` to application tag.
- **WebView**: Explicitly enable `LAYER_TYPE_HARDWARE`.
- **Lifecycle**: Pause `WebView` timers and progress tracking in `onPause` to release resources when backgrounded.

### UI Refactor: Recent Opens
- **HomeFragment**: Replace the inline list with a "Recent Opens" button.
- **RecentsFragment**: Create a new fragment to display the list of recent files with a "Clear All" button.
- **Navigation**: "Recent Opens" button pushes `RecentsFragment` to the stack.

### Files Context Menu
- **Fix**: Verify and reinforce the logic to add "Rename" and "Delete" to the long-press menu in `FilesFragment`. Ensure it works for both Files and Directories in Local/SMB modes.

### Sort Order Persistence
- **Database**: Update `FolderSessionEntity` to include `sortOption` (Int) field.
- **Migration**: Bump Room version 3 -> 4.
- **Logic**:
    - Save `sortSpinner` selection to DB immediately.
    - Restore `sortSpinner` selection from DB when loading a session.

## Impact
- **Stability**: Significantly reduced crash rate for large files.
- **UI**: Cleaner Home screen.
- **UX**: Persistent sort order.

## Implementation Details

### Database Schema
```kotlin
// FolderSessionEntity
val sortOption: Int = 2 // Default to Modified Descending (index 2)
```

### Migration 3->4
```sql
ALTER TABLE folder_sessions ADD COLUMN sortOption INTEGER NOT NULL DEFAULT 2
```
