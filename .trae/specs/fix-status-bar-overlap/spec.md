# Fix Status Bar Overlap Spec

## Why
On Android 15+ (and Android 16), the system enforces edge-to-edge display by default. If the app does not properly handle window insets (status bar and navigation bar), the content will overlap with these system bars. The user reported overlap on an Android 16 device.

## What Changes
- Enable Edge-to-Edge support in `MainActivity`.
- Apply window insets to the root view of `MainActivity` to ensure content does not overlap with system bars.
- Ensure `android:fitsSystemWindows="true"` is NOT used in a way that conflicts with manual inset handling (or use it correctly if we rely on default behavior).

## Impact
- **User Experience**: The app will render correctly on Android 16+ without content being hidden behind the status bar or navigation bar.
- **Code**: `MainActivity.kt` and `activity_main.xml`.

## ADDED Requirements
### Requirement: Handle Window Insets
The system SHALL apply padding to the main content container equal to the system bar insets (status bar, navigation bar) to prevent overlap.

#### Scenario: App Launch on Android 16
- **WHEN** the user launches the app on an Android 16 device
- **THEN** the top of the app content should be below the status bar.
- **AND** the bottom of the app content (navigation bar) should be above the system navigation handle.

## MODIFIED Requirements
None.
