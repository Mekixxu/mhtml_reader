# Fix FTP Compatibility Spec (Encoding & Parsing)

## Why
1.  **Encoding Issue**: The app currently hardcodes "UTF-8" when encoding file paths for FTP operations (Download/Upload). If a user connects to a non-UTF-8 server (e.g., GBK) and manually selects the correct encoding for *display*, the app still sends UTF-8 encoded paths in requests, causing "File not found" errors.
2.  **Parsing Robustness**: The current FTP listing parser uses `split(whitespace)` which incorrectly breaks filenames containing multiple consecutive spaces (collapsing them into single spaces). This results in incorrect file paths being requested, also causing "File not found" errors even if the encoding is correct (e.g., UTF-8).
3.  **Date Parsing**: The current parser only supports English months ("Jan", "Feb"). It fails on servers using other locales (e.g., Chinese dates), falling back to a fragile column-counting method that may misidentify the start of the filename.

## What Changes
- **FTP URL Construction (`FilesFragment.kt`)**:
  - Update `buildFtpUrl` to respect `NetworkConfigEntity.encoding`.
  - Use the configured encoding (or UTF-8 default) for `URLEncoder.encode()`.
- **FTP Listing Parser (`FilesFragment.kt`)**:
  - Rewrite `parseUnixStyle` to robustly identify the filename start index without using `split/join` on the filename part.
  - Improve date parsing to support ISO-8601 style dates (`YYYY-MM-DD` or similar) common on modern servers.
  - Implement a smarter fallback strategy for finding the filename start index (search from end of line backwards?).

## Impact
- **FTP Compatibility**: Significantly improved success rate for opening files on diverse FTP servers (UTF-8/GBK, English/Non-English, Spaces in filenames).
- **User Experience**: Users who select the correct encoding will now be able to open files as expected.

## MODIFIED Requirements
### Requirement: FTP Path Encoding
The system SHALL use the user-configured character encoding (if specified) when constructing FTP URLs.
- **WHEN** `config.encoding` is set (e.g., "GBK"), `buildFtpUrl` MUST use it for URL encoding.
- **Default**: "UTF-8".

### Requirement: FTP Listing Parsing
The system SHALL correctly parse filenames from FTP `LIST` output, preserving all characters including multiple spaces.
- **Scenario**: Filename with multiple spaces (e.g., "My  File.txt").
- **Current**: Parsed as "My File.txt" (Fail).
- **New**: Parsed as "My  File.txt" (Success).

The system SHALL support common date formats beyond English abbreviations.
- **Scenario**: `2024-01-01` or `01-01-2024`.
- **New**: Correctly identified as date column.
