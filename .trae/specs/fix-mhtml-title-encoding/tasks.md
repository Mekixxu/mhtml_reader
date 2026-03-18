# Tasks
- [x] Task 1: Update HtmlTitleExtractor to support multiple charsets
  - [x] SubTask 1.1: Read file content as raw bytes instead of decoding immediately.
  - [x] SubTask 1.2: Implement a helper to scan for `charset=...` in the raw bytes (using ISO-8859-1 for safe ASCII matching).
  - [x] SubTask 1.3: Implement logic to decode using the detected charset, or fallback to UTF-8 then GBK if no charset is found or if UTF-8 produces replacement characters.
  - [x] SubTask 1.4: Apply the regex on the correctly decoded string.

# Task Dependencies
- None
