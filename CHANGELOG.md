# Changelog

## 1.1.0 - Beacon Reporting - 2026-09-05

- Embedded Beacon for Console crash reporting and standard usage stats. No separate Beacon installation is required.
- Added `/beacon consent` support with Console's icon. Operators can review or disable crash and stats reporting independently; both default to enabled.
- Added capture for Console setup/start failures and runtime crashes attributed to Console. Reporting failures do not prevent Console from loading or shutting down.
- Included Beacon's consent UI and runtime in both the release JAR and development asset pack.
- Kept Console searches, copied selections, and historical log files out of the reporting integration.
- Fixed the build clearing Console's CurseForge update-check project ID.

## 1.0.1 - Stable 0.6 Compatibility Hotfix - 2026-08-27

- Expanded declared Hytale Server compatibility to `>=0.5.0 <0.7.0`.

## 1.0.0

- Added a responsive in-game console for live server logs.
- Added on-demand browsing for current and previous server log files.
- Added same-host client log browsing with an optional local client log directory.
- Added severity, logger, and text filters with bounded results and pagination.
- Added pause and follow controls plus ordered multi-row selection for copying full log text.
- Added fixed limits for retained records, rendered rows, discovered files, historical results, and pending work.
- Added support for Hytale Server versions `>=0.5.0 <0.7.0`.
