# Architecture

> **For informational use only.** This app is not a medical device, not a replacement for the
> official Libre apps, and not intended for emergency glucose decisions. It makes no FDA/CE or
> medical-compliance claims. All alerts and displays are convenience features only. Always verify
> critical glucose readings with your official device/app.

## Overview

HealthConnect-LibreLinkUp is a two-module Android/Wear OS project that mirrors the latest glucose
reading from a LibreLinkUp/LibreView account into Android Health Connect and onto a Wear OS watch
(tile + complication).

```
app/        Phone application (login UI, background sync, Health Connect writer, wear data push)
wearable/   Wear OS application (data-layer listener, tile, complication, tap-to-refresh)
```

## Current architecture (after Phase 1)

### Phone module (`app/`)

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Jetpack Compose login screen, Health Connect permission flow, manual "Sync now", surfaces sync status and the safety disclaimer. |
| `LibreLinkUp.java` | LibreLinkUp REST client + encrypted session storage (`EncryptedSharedPreferences`). Schedules the periodic sync and exposes `syncNow()`/`logout()`. |
| `SyncWorker.java` | WorkManager `Worker` that fetches the latest reading, writes it to Health Connect, and pushes it to the watch. Owns retry/backoff and failure notifications. |
| `SyncStatusStore.java` | Non-sensitive store of sync state (last attempt/success time, last error, login status) for the UI. Contains no PHI or credentials. |
| `Notifications.java` | User-visible sync-failure / login-expired notifications on a dedicated channel. |
| `PhoneSyncRequestListenerService.java` | Receives `/sync_now` data-layer messages from the watch and enqueues an immediate sync. |
| `BootReceiver.kt` | Re-schedules the periodic sync after boot / app update. |

### Wear module (`wearable/`)

| File | Responsibility |
| --- | --- |
| `DataLayerListenerService.kt` | Receives `/glucose` data items from the phone, caches them, and refreshes the tile + complication. |
| `tile/GlucoseTileService.kt` | Renders the glucose tile (value, trend arrow, relative time) with a tap-to-reload action and a no-data layout. |
| `complication/GlucoseComplicationService.kt` | Short-text complication. Never renders blank (shows `--` when no data) and taps trigger a refresh. |
| `RefreshReceiver.kt` | Handles refresh taps: asks the phone to sync via the data layer and refreshes local surfaces. |

## Data flow

```
LibreLinkUp / LibreView (HTTPS)
        |  LibreLinkUp.connections()
        v
SyncWorker (WorkManager, every 15 min + manual)
        |  insertRecords()                      |  PutDataMapRequest "/glucose"
        v                                        v
Android Health Connect                    Wear OS DataLayerListenerService
                                                 |  cache + requestUpdate
                                                 v
                                          Tile + Complication
                                                 |  tap -> RefreshReceiver
                                                 v
                                          MessageClient "/sync_now"
                                                 v
                                   PhoneSyncRequestListenerService -> SyncWorker
```

## Known bugs / risks addressed in Phase 1

1. **Login crash** – the login coroutine only caught `IOException`; any other exception (keystore,
   parsing, scheduling) escaped an uncaught background coroutine and closed the app. Now all
   exceptions are caught and surfaced to the UI.
2. **Auth-ticket wipe** – `SyncWorker` previously called `setAuthTicket(result.ticket)`
   unconditionally; when the server omitted a ticket this cleared a valid token and broke every
   later sync. The ticket is now only replaced when a valid token is returned.
3. **Blank complication** – the complication returned `NoDataComplicationData` when no reading was
   cached, which renders blank on many watch faces. It now always shows `--` / "No glucose data
   yet".
4. **Silent failures** – sync failures were swallowed. They are now retried with exponential
   backoff, recorded in `SyncStatusStore`, shown on the phone screen, and (when persistent) raised
   as a notification. Auth failures raise a distinct "login expired" notification.
5. **No manual refresh** – added a phone "Sync now" button and watch tap-to-refresh
   (complication -> phone sync request).

## Privacy / safety constraints

* No cloud backend is introduced; all networking goes directly to LibreLinkUp.
* Credentials and tokens remain in `EncryptedSharedPreferences`; `SyncStatusStore` and logs never
  contain glucose values, emails, names, or tokens (only exception class names, counts, and
  timestamps are logged).
* The app surfaces a persistent "informational use only" disclaimer.

## Proposed follow-up phases (not in this PR)

These are intentionally deferred to keep Phase 1 reviewable and low-risk:

* **Phase 2 – Core refactor:** extract `GlucoseReading` / `SyncState` / `UserSettings` models,
  isolate the LibreLinkUp client / Health Connect repository / wear sync behind interfaces, add
  DataStore preferences and a demo/mock data mode.
* **Phase 3 – Phone redesign:** Compose dashboard, history/trends with charts, alerts config,
  settings, diagnostic center.
* **Phase 4 – Wear upgrade:** richer Compose-for-Wear screens, multiple tile layouts, stale-data
  warnings, watch settings.
* **Phase 5 – Polish:** accessibility, previews, unit tests, GitHub Actions CI (build, lint, test,
  assemble phone + wear APKs), README screenshots.
