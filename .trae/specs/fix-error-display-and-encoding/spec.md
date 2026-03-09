# Fix Error Display and FTP Encoding Spec

## Why
1. Users cannot see the full error message when an operation fails, making debugging difficult.
2. FTP file names with non-ASCII characters (e.g., Chinese GBK) are displayed as garbled text (mojibake), causing files to be unopenable because the path is incorrect.

## What Changes
1. **Error Display**:
   - In `FilesFragment` and `ReaderFragment`, make the error status label clickable. Clicking it will show a dialog with the full error message.
   - Add a visual cue (e.g., "(Tap for details)") to the error message.

2. **FTP/SMB Encoding Support**:
   - Update `NetworkConfigEntity` to include an `encoding` field (default: "ISO-8859-1" or "Auto").
   - Update `AppDatabase` to version 3 with migration support.
   - Update `MoreFragment` network config dialog to include a dropdown for selecting encoding (Auto, UTF-8, GBK, Big5, ISO-8859-1, Shift_JIS, Windows-1251).
   - Update `FilesFragment`'s `decodeFtpName` to respect the configured encoding.

## Impact
- **Database**: Schema change for `NetworkConfigEntity`. Requires migration.
- **UI**: 
  - `MoreFragment`: New spinner in network config dialog.
  - `FilesFragment` / `ReaderFragment`: Clickable status labels.
- **Logic**: FTP name decoding strategy in `FilesFragment`.

## ADDED Requirements
### Requirement: Full Error Details
The system SHALL allow users to view the complete error message text when an error occurs.
#### Scenario: Error Click
- **WHEN** an error message is displayed in the status bar
- **AND** the user taps on the status bar
- **THEN** a dialog SHALL appear containing the full stack trace or error description.

### Requirement: Custom Network Encoding
The system SHALL allow users to specify the character encoding for FTP/SMB connections.
#### Scenario: Add/Edit Network
- **WHEN** adding or editing a network connection
- **THEN** the user can select an encoding from a list (including GBK for Chinese support).

## MODIFIED Requirements
### Requirement: FTP Name Decoding
Modified to use the user-specified encoding if provided, falling back to heuristic detection ("Auto") only if specified or as a default.
