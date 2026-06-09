# Changelog

All notable changes to this project are documented here.

## [Unreleased]

### Phase 1 – Stabilization

#### Fixed
- Login no longer crashes/closes the app when an unexpected (non-`IOException`) error occurs; all
  failures are caught and shown in the UI.
- Background sync no longer wipes a valid auth token when the server response omits a new ticket,
  which previously broke all subsequent syncs.
- The Wear OS complication no longer renders blank when no reading is cached; it shows `--` /
  "No glucose data yet".

#### Added
- Visible sync status on the phone screen (last successful sync, last error, login-expired).
- Manual **Sync now** button on the phone.
- Tap-to-refresh on the Wear OS complication (requests an immediate phone sync) and tap-to-reload on
  the tile.
- Retry with exponential backoff for transient sync failures (WorkManager).
- User-visible notifications for persistent sync failures and expired logins, on a dedicated
  notification channel (with Android 13+ `POST_NOTIFICATIONS` handling).
- Structured, PHI-safe logging across login, sync, Health Connect writes, and wear data sync.
- Persistent "informational use only" safety disclaimer in the app.
- `docs/ARCHITECTURE.md` and `docs/QA.md`.

> Note: This change set is scoped to Phase 1 (stabilization). The broader Compose redesign, history/
> trends, alerts configuration, richer Wear UI, and CI are tracked as follow-up phases in
> `docs/ARCHITECTURE.md`.
