# Tasks

- [x] Task 1: Update FTP URL Construction
  - [x] Modify `FilesFragment.kt`:
    - [x] Update `buildFtpUrl` to use `config.encoding` for `URLEncoder.encode()`.
    - [x] Use `config.encoding` if valid (e.g., "GBK"), else "UTF-8".
    - [x] Ensure `uploadDocumentToFtp` also uses the correct encoding for the target filename.

- [x] Task 2: Improve FTP Parsing Robustness
  - [x] Modify `FilesFragment.kt` -> `parseUnixStyle`:
    - [x] Implement robust date detection (add ISO-8601 like `YYYY-MM-DD` and `MM-DD-YYYY` patterns).
    - [x] Change filename extraction logic: Find the end index of the "time" column (or "year" column) in the raw line, and take `substring(endIndex + 1).trim()`. Do NOT use `split` and `join` on the filename part.
    - [x] Ensure spaces are preserved exactly as they appear in the raw line.
  - [x] Verify `parseDosStyle` logic as well (it already uses `split` but maybe simpler structure). If needed, improve it too.

- [x] Task 3: Verify Fixes
  - [x] Verify that files with spaces in names open correctly.
  - [x] Verify that files on non-English servers (date format) open correctly.
  - [x] Verify that files on GBK servers open correctly (with GBK setting).
