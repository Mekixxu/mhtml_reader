# Fix MHTML Title Encoding Spec

## Why
Users report that file titles in the "Folders" view (DirectoryModePage) appear garbled (mojibake) for some MHTML files, specifically showing replacement characters (�) or incorrect symbols. This occurs because the current title extractor assumes UTF-8 encoding, while many MHTML files (especially from older sources or specific regions) use other encodings like GBK or Big5.

## What Changes
- **Modify `HtmlTitleExtractor.kt`**:
  - Remove the hardcoded UTF-8 decoding.
  - Implement a heuristic to detect charset from the file content (looking for `charset=...` or `encoding=...` in headers or meta tags).
  - Implement a fallback mechanism: Try UTF-8 first; if it produces replacement characters or fails, try GBK (and potentially other common charsets like Big5 or Shift_JIS).
  - Ensure the regex search for `<title>` is performed on the correctly decoded string.

## Impact
- **Affected specs**: `FilesFragment` title display logic.
- **Affected code**: `core/title/impl/HtmlTitleExtractor.kt`.

## ADDED Requirements
### Requirement: Charset Detection
The system SHALL attempt to detect the character encoding of the MHTML/HTML file before extracting the title.
- **Scenario: Explicit Charset**: If the file contains `charset="gb2312"`, the system SHALL use GBK/GB2312 to decode the content.
- **Scenario: Fallback**: If no charset is detected, the system SHALL try UTF-8. If the extracted title contains invalid characters (replacement char �), it SHALL retry with GBK.

## MODIFIED Requirements
### Requirement: Title Extraction
**Old**: Read file as UTF-8 -> Regex extract title.
**New**: Read file bytes -> Detect/Guess Charset -> Decode -> Regex extract title.
