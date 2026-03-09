# Tasks

- [x] Task 1: Capture Raw Bytes
  - [x] Modify `BrowserEntry` data class in `FilesFragment.kt` to include `val rawNameBytes: ByteArray? = null`.
  - [x] Update `parseUnixStyle` to extract the name bytes (from `line.toByteArray(Charsets.ISO_8859_1)`) and pass them to `BrowserEntry`.
  - [x] Update `parseDosStyle` to do the same.

- [x] Task 2: Implement Diagnostic Dialog
  - [x] Create a helper function `showDiagnosticDialog(entry: BrowserEntry)` in `FilesFragment`.
  - [x] It should format the `rawNameBytes` as a Hex string (space separated).
  - [x] It should try to decode `rawNameBytes` using: UTF-8, GBK, ISO-8859-1, Big5, Shift_JIS, Windows-1251.
  - [x] Construct a message string showing these details.
  - [x] Show an `AlertDialog` with this message and a "Copy" button.

- [x] Task 3: Add Menu Option
  - [x] Update `onItemLongClickListener` in `FilesFragment`.
  - [x] Add "Diagnose Encoding" to the options list.
  - [x] When selected, call `showDiagnosticDialog`.
