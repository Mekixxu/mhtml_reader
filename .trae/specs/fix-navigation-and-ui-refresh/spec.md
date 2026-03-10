# Fix Navigation and UI Refresh Spec

## Why
1.  **Navigation Bug**: When opening a file from Folders, the app correctly loads the Reader but the bottom navigation bar remains on "Folders". This is because the fragment transaction is asynchronous, and the navigation sync logic checks the *old* fragment tag.
2.  **UI Stale**: The user reports seeing "grayed out" buttons that were supposed to be removed. This indicates the previous layout changes were not correctly applied/deployed.

## What Changes

### Navigation Logic (`MainActivity.kt`)
- Update `showContent` to pass the `tag` explicitly to `syncBottomNavSelection`.
- Update `syncBottomNavSelection` to use the passed `tag` instead of querying the FragmentManager (which might return the stale fragment).
- Update `syncBottomNavSelection` to set `bottomNav.selectedItemId` (programmatically) while temporarily disabling the listener. This ensures the internal state matches the visual state, preventing "re-selection" bugs when the user clicks the tab later.

### UI Refresh (`fragment_files.xml` & `FilesFragment.kt`)
- **Force Layout Update**: Modify `fragment_files.xml` (e.g., add a non-functional attribute or comment) to ensure the build system detects the change and repackages the resources.
- **Verify Menu**: The `FilesFragment.kt` logic for the context menu ("Rename"/"Delete") is correct, but relies on the `browseSource` being `LOCAL` or `SMB`. This logic remains, but we will verify the file is compiled.

## Impact
- **User Experience**:
    - Clicking a file in Folders will now visually switch the bottom tab to "Tabs" (Reader).
    - The "Files" screen will show the clean interface (no disabled buttons) and the context menu will have "Rename"/"Delete".
- **Code**:
    - `MainActivity.kt`: Robust navigation state syncing.
    - `fragment_files.xml`: Trivial update.

## Implementation Details
- In `MainActivity.kt`:
  ```kotlin
  private fun showContent(fragment: Fragment, tag: String) {
      // ... commit ...
      syncBottomNavSelection(tag)
  }
  
  private fun syncBottomNavSelection(explicitTag: String? = null) {
      val tag = explicitTag ?: supportFragmentManager.findFragmentById(R.id.main_content)?.tag
      // ... determine itemId ...
      if (bottomNav.selectedItemId != itemId) {
          val listener = bottomNav.onItemSelectedListener
          bottomNav.setOnItemSelectedListener(null)
          bottomNav.selectedItemId = itemId
          bottomNav.setOnItemSelectedListener(listener)
      }
  }
  ```
