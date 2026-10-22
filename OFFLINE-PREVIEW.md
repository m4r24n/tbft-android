# TBFT native offline preview

This branch replaces the WebView organiser with native screens backed by a per-account SQLite database. The debug APK uses `info.marzan.tbft.offlinepreview` and the label **TBFT Offline Preview**, so it can be installed alongside the existing app. It is a preview, not a complete replacement yet.

## Implemented

- Native task creation/editing, owner/date/time/priority, project/phase assignment, completion notes, complete/reopen/archive/restore.
- Native reminders at the start of each owner's task list, with Add reminder at the end.
- Project overview with task progress and next action, project details, phase creation/editing, and a calendar date view.
- Notebook messages, downloaded file metadata and recent server activity.
- Workspace timezone/day rollover settings.
- Per-account SQLite rows with durable pending changes. Saves commit locally before any cloud request.
- Direct Supabase authentication and RLS-protected REST sync, independent of the web host. Passwords are never persisted; session tokens are encrypted with Android Keystore.
- Original server bases, changed-field merges, conditional writes, idempotent IDs, explicit conflict review, and preservation of newer edits during an upload.
- Separate executors for disk saves and network operations. A slow network does not occupy the save queue.
- Home widget and AOD widget rendering from the same local database, including reminders. No widget web API dependency.
- Persisted background sync jobs and local rollover refresh jobs. Android may defer jobs under Doze or battery restrictions; AOD availability also depends on the launcher/device.
- A minimal HTTPS browser in its own process and WebView data directory: address, back, forward, reload, close. No feed, homepage, native JavaScript bridge, popups, downloads or device permission prompts.
- JSON export of local records, pending changes and their server bases; session credentials are excluded.

Connect the same TBFT account once and wait for a successful initial sync before relying on offline access. Edits require the **Save on device** button. Unsaved form drafts are not guaranteed to survive process termination. Uninstalling or clearing app data removes the local database; export pending work first.

## Remaining parity work

| Area | Current boundary |
| --- | --- |
| Recurring tasks | Existing occurrences and recurrence metadata are preserved. Generation and recurrence editing still run through the web app. |
| Completion PDF / Google Drive | Completion notes and status sync, but this preview does not invoke the web completion-PDF export. A durable, idempotent export worker is still required. Do not use this preview as the sole completion workflow when a Drive PDF is required. |
| Attachments | Metadata and HTTPS links only. Offline file bytes, uploads and Google Drive integration are not implemented. |
| Activity | Downloads the server activity log. Native writes do not yet append the web action audit entries. |
| Advanced project features | No nested-phase management, drag reordering, manual progress or legacy themed views. |
| Settings and account | One account and first available workspace; no account/workspace switching, invitation management, push notifications or calendar subscriptions. |
| Backup | Inspection/export format only; no automated restore or encrypted scheduled backup yet. |
| Sync | Foreground request plus Android background jobs, not continuous Realtime. Overlapping edits need review. Remote deletion conflicts retain a local copy for export. |
| Packaging | Debug signing is build-specific. Keep this preview separate; a stable release signing key and upgrade/migration verification are required before replacement. |

## Verification

GitHub Actions runs Java unit tests, Robolectric SQLite persistence tests, Android lint, and debug assembly:

```sh
cd TBFT-Android
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Tests cover rollover across both DST transitions, carried/future/completed tasks, conflict detection, idempotent timestamp/numeric normalization, persistence across reopen, dirty rows during snapshots, saves during uploads, reminder deletions, conflict-review races and backup contents.

Still required on a signed-in device before production adoption:

1. First download: compare both owners' tasks, reminders, projects and phases with the web.
2. Airplane mode: create/edit/save tasks, long completion notes and reminders; complete a task; restart the app and phone; verify saved rows and widget/AOD.
3. Reconnect: confirm queued writes reach the web once and the queue drains. Repeat after an expired session and after reconnecting the same account.
4. Conflict: change the same note on web and offline phone; verify explicit review and both choices. Save again while a sync request is in flight.
5. Outage: block the web host while Supabase remains reachable; then block Supabase. Confirm native local work remains available.
6. Browser: navigate a real HTTPS website, test TLS failure/back/close, and confirm no organiser session is shared.
7. Verify API 26 and current Android edge-to-edge, keyboard, large font, rotation, widget rollover and device AOD behavior.

No database schema changes or privileged Supabase keys are introduced. This branch should stay a draft until authenticated sync, device tests and the missing daily workflows are complete.
