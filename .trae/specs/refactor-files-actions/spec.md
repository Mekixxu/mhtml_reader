# Refactor Files Actions UI Spec

## Why
The user requested a simplification of the file management UI in `FilesFragment`.
- The "Rename" and "Delete" actions take up too much screen space as buttons and should be moved to the context menu (long-press).
- The "Duplicate", "Move to Parent", and "Open in New Session" actions are rarely used or no longer desired, so they should be removed entirely to declutter the interface.

## What Changes
- **Layout (`fragment_files.xml`)**:
  - Remove "Rename" and "Delete" buttons.
  - Remove "Duplicate" and "Move to Parent" buttons.
  - Remove "Open in New Session" button.
  - Keep "Up" and "Create Folder/Upload" buttons.

- **Logic (`FilesFragment.kt`)**:
  - Remove references and listeners for the deleted buttons.
  - Update `onItemLongClickListener`:
    - Add "Rename" option.
    - Add "Delete" option.
    - Wire up the Rename/Delete logic (supporting both Local and SMB/FTP if applicable).
  - Remove logic for "Duplicate", "Move to Parent", and "New Session".

## Impact
- **UI**: Cleaner interface with fewer buttons.
- **UX**: Rename/Delete are now hidden behind a long-press interaction.
- **Functionality**: "Duplicate", "Move to Parent", and "Open in New Session" features are removed.

## REMOVED Requirements
### Requirement: Quick Actions
**Reason**: User request to simplify UI.
**Migration**: Use Context Menu for Rename/Delete.

### Requirement: Advanced File Operations
**Reason**: User request to remove "Duplicate", "Move to Parent", "Open in New Session".

## MODIFIED Requirements
### Requirement: File Context Menu
The system SHALL provide "Rename" and "Delete" options in the file/folder context menu (long-press).
- **WHEN** user long-presses a file/folder
- **THEN** a menu appears with "Select", "Open in new tab", "Add to favorites", "Details", "Diagnose Encoding" (if FTP), "Rename", "Delete".
