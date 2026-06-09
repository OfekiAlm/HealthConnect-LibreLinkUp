# Manual QA checklist

> For informational use only. Verify all critical glucose readings with your official device/app.

Use a real LibreLinkUp account (or a follower account) plus a phone and, ideally, a Wear OS watch.

## Phase 1 – Stabilization

### Login & permissions
- [ ] Fresh install launches without crashing.
- [ ] Selecting a region updates the server field.
- [ ] Tapping **Login to LibreLinkUp** with valid credentials shows "Logged in as …" and **does not
      crash or close the app**.
- [ ] Tapping login with invalid credentials shows a clear error message and stays open.
- [ ] Tapping login with no network shows a connection error and stays open.
- [ ] Health Connect permission prompt appears; granting it schedules sync.
- [ ] On Android 13+, a notification permission prompt appears.

### Sync status & manual sync
- [ ] After login, a **Sync now** button is visible.
- [ ] Tapping **Sync now** updates the status line (e.g. "Sync requested…" then "Last successful
      sync: …").
- [ ] After a successful sync, the latest reading appears in Health Connect.
- [ ] Turning off network and tapping **Sync now** eventually shows a "Last sync failed" status and,
      after retries, a sync-failure notification.

### Failure visibility
- [ ] A persistent sync failure produces a single "Glucose sync failed" notification that opens the
      app when tapped.
- [ ] An expired session produces a "login expired" notification and an in-app "Login expired"
      status.
- [ ] Logging back in clears the login-expired status/notification.

### Wear OS
- [ ] Adding the complication never shows a blank value; with no data it shows `--`.
- [ ] After a phone sync, the complication shows the current value and trend arrow.
- [ ] The tile shows value, trend arrow, and a relative "last updated" time.
- [ ] Tapping the tile reloads it.
- [ ] Tapping the complication triggers a refresh (phone receives a sync request and pushes new
      data).

### Safety & privacy
- [ ] The "informational use only" disclaimer is visible on the phone screen.
- [ ] No medical-compliance claims appear anywhere.
- [ ] `adb logcat` during sync shows **no** glucose values, emails, names, or tokens.

## Regression (must not break)
- [ ] Existing Health Connect writes still occur every ~15 minutes.
- [ ] Existing tile and complication still update when the phone pushes data.
- [ ] Sync re-schedules after a reboot.
