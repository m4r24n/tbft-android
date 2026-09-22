# TBFT Native 2.1

The native organiser now uses the website's paper, sage, brass and ink palette. More opens a compact menu; Archive, Files and Activity have individual screens. Projects separate Tasks, Phases, Files, Activity and Details. Calendar has a month grid and a selected-day board.

## Wardrobe

- Coloured garment silhouettes, quantities, built-in and custom categories.
- Home, Outdoor and Both; Available, In Use and Laundry share the same physical-piece records.
- Available-item outfit suggestions, saved outfits and atomic Wear outfit action.
- Configurable laundry threshold (zero disables automatic tasks), one active laundry task, and manual finish below the threshold.
- Completing Do laundry from the board returns its matching load. Reopening an old task cannot wash a later load.
- Clothing changes and board-task creation commit in one local SQLite transaction. Network errors retain pending changes.
- Supabase migration `20260921205335_wardrobe_native_v1.sql` provides owner-only RLS, version-checked writes and reciprocal task-completion triggers. The wardrobe document is shared across signed-in Android devices. Website Wardrobe screens remain a later step.
- Concurrent wardrobe edits require explicit review. If laundry history changed remotely, preserve the local backup and use the server copy before reapplying desired clothing changes. Completed laundry history is immutable.

## Downloads

Direct HTTPS downloads use Android's Save file picker, with progress, cancellation and an Open action. Website cookies are selected separately for each redirected host; organiser credentials are never passed to the browser. Keep the browser open until the file finishes. Blob-generated downloads are not supported in this version.

## Installation and updates

The previous preview was signed with an ephemeral CI debug key. This version uses the new application ID `info.marzan.tbft.nativeapp` and a persistent private release key, and installs alongside that preview. Do not uninstall the preview until it has synced or its pending work has been exported.

Connect the same account in the new app. If needed, use More → Sync & backup → Import backup from previous app. The import restores pending records for review and never imports credentials or overwrites conflicting pending edits.

CI publishes an unsigned release APK. Sign it with the persistent `tbft-native` key kept privately by the owner. Never commit the key or publish it as an Actions artifact. Subsequent releases must retain the same application ID and signing key.

## Coverage and limits

Tasks, completion notes, notebook messages, reminders, projects, phases, calendar and Wardrobe save locally. Widgets/AOD project the local task data. Files currently show downloaded metadata and online links. Recurring task generation and Google Drive PDF export continue to require the website; this release does not claim complete offline parity.

Verification includes unit tests, SQLite restart/rollback checks, isolated PostgreSQL contract checks, real Supabase task-trigger checks inside a rolled-back fixture transaction, and Android 15 offline UI checks with fixture-only screenshots. No real user's records are used for screenshots.
