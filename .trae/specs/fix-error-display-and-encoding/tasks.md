# Tasks

- [x] Task 1: Database Migration
  - [x] Update `NetworkConfigEntity` to add `encoding: String` field (default "Auto").
  - [x] Update `AppDatabase` version to 3.
  - [x] Implement `MIGRATION_2_3` in `AppDatabase` (or `RoomConverters` helper) to add the column.

- [x] Task 2: Network Config UI (MoreFragment)
  - [x] Update `showEditDialog` in `MoreFragment.kt` to include a Spinner for Encoding.
  - [x] Supported options: "Auto", "UTF-8", "GBK", "Big5", "ISO-8859-1", "Shift_JIS", "Windows-1251".
  - [x] Save the selected encoding to `NetworkConfigEntity`.

- [x] Task 3: FTP Encoding Logic (FilesFragment)
  - [x] Update `FilesFragment` to read the `encoding` from `NetworkConfigEntity`.
  - [x] Modify `decodeFtpName` to use the specified encoding.
    - If "Auto": Keep existing logic (UTF-8 -> GBK -> ISO-8859-1).
    - If specific (e.g., "GBK"): Force decode as GBK.

- [x] Task 4: Error Display Improvement
  - [x] Modify `FilesFragment.kt`: In `updateStatus`, if `isError` is true, set an `OnClickListener` on `operationStatusLabel` to show an AlertDialog with the full message.
  - [x] Modify `ReaderFragment.kt`: In `runOpen` (error state) and other error handlers, set an `OnClickListener` on `statusLabel` to show an AlertDialog.
