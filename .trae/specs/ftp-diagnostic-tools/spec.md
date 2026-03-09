# FTP Diagnostic Tools Spec

## Why
1. Users are still experiencing "mojibake" (garbled filenames) with certain FTP servers, even after adding manual encoding selection.
2. Guessing the encoding is ineffective; we need raw data to determine the actual byte sequence sent by the server.
3. Users are willing to run experiments to provide this data.

## What Changes
1. **Diagnostic Action**:
   - Add a "Diagnose Encoding" option to the long-press menu of any file/folder in `FilesFragment`.
   
2. **Data Capture**:
   - Update `BrowserEntry` to store the raw bytes of the filename (`rawNameBytes: ByteArray`).
   - Update `parseFtpLine` (and its helpers `parseUnixStyle`/`parseDosStyle`) to capture and store these bytes before any decoding attempts.

3. **Diagnostic UI**:
   - When "Diagnose Encoding" is selected, show a dialog containing:
     - **Hex Dump**: The raw bytes of the filename (e.g., `D6 D0 ...`).
     - **Decoding Previews**: How these bytes look when decoded as:
       - UTF-8
       - GBK
       - ISO-8859-1
       - Big5
       - Windows-1251
       - Shift_JIS
     - **Current Setting**: Display the currently configured encoding for this connection.

## Impact
- **Code**: `FilesFragment.kt` (BrowserEntry, parsing logic, menu handler).
- **UX**: New diagnostic tool available for advanced users/debugging.
- **Performance**: Minimal impact (storing a small byte array per file entry).

## ADDED Requirements
### Requirement: Raw Byte Capture
The system SHALL store the raw bytes of the filename for every FTP entry listed.

### Requirement: Encoding Diagnostic Dialog
The system SHALL provide a way to view the raw bytes and various decoding attempts for a selected file.
#### Scenario: Diagnose File
- **WHEN** user long-presses a file in `FilesFragment`
- **AND** selects "Diagnose Encoding"
- **THEN** a dialog appears showing the Hex representation of the filename and previews of it decoded in common charsets.
