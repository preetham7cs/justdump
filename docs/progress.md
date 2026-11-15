# Progress

Last updated: 2026-11-15

## Current state

Milestones 0, 1, 2, 3A, and the synthetic-only 3R retrieval baseline are complete. The current development checkpoint adds local-first image/link capture and verified automatic media upload/read-back through the locally running FastAPI API. Supabase Free, email/password Auth, owner-scoped RLS, private Storage, FastAPI/Pydantic, and Room as the Android UI source of truth are confirmed. There is no hosted API, backup guarantee, media processing, or Android Ask feature yet.

## Verified Milestone 1 work

- Android Studio Quail 4 is installed; its bundled runtime is OpenJDK 25.0.3.
- Android SDK Platform 36, Build Tools 36.0.0, and Platform Tools/ADB 37.0.1 are installed.
- Wireless ADB detected and authorised the Samsung SM-S918U1. Device properties report Android 16, API 36, and Samsung One UI property `80500` (One UI 8.5).
- `apps/android/` contains one `app` module using Kotlin and Jetpack Compose, application ID `io.github.preetham7cs.justdump`, minimum SDK 26, compile SDK 36, and target SDK 36.
- The pinned build uses Android Gradle Plugin 9.4.0, Gradle wrapper 9.6.0, Kotlin/Compose compiler 2.3.21, Compose BOM 2026.04.01, and Activity Compose 1.11.0.
- `gradlew.bat clean lintDebug assembleDebug` completed successfully. Final lint reported zero errors and six intentional version-availability warnings caused by the approved API 36/toolchain pins. The generated debug APK verified with an `Android Debug` certificate.
- ADB streamed installation succeeded. A cold launch completed, Android reported `MainActivity` focused, the live UI hierarchy contained `JustDump`, and the crash buffer contained no matching fatal exception.
- The owner visually confirmed the `JustDump` welcome screen and successfully reopened the app from its launcher icon on 2026-09-13.

Generated APKs, build reports, Gradle caches, local IDE state, SDK paths, and debug signing material are excluded from Git. At the end of Milestone 1, no app feature beyond the welcome screen had been implemented.

## Verified Milestone 2 work

- The single Android module now provides a capture-first Compose screen: type/paste a text note, save it, see recent notes, and open a read-only detail screen. Favourites, sharing, images, links, backend, authentication, synchronisation, indexing, and AI are not included.
- `Room 3` is the device-local source of truth for notes and the one capture draft. Its generated version-1 schema is checked in for future non-destructive migration work; no destructive-migration fallback is configured.
- The ViewModel holds transient screen state. Only the selected note ID uses `SavedStateHandle`; note bodies and draft content remain in Room rather than in Android saved instance state.
- A changed draft is persisted after a 500 ms debounce. A monotonically increasing generation and a transaction that saves the note while acknowledging the draft prevent an older delayed write from restoring text after a save. If the owner types while a save is in progress, the newer text stays in the editor as the next draft.
- The 500 ms debounce is a responsiveness/data-safety trade-off, not a promise that the final keystroke survives an immediate force-stop or device failure. Waiting at least one second before leaving the app gives the debounced write time to complete.
- The manifest sets `allowBackup="false"` and explicitly excludes the Room database from cloud backup and device-transfer rules. This is a privacy policy for this local prototype, not a backup/restore feature; platform/vendor behaviour still needs a later resilience-milestone verification.
- Instrumented tests ran against the authorised Samsung SM-S918U1 (Android 16/API 36) and passed for: transaction coordination against stale draft writes; failed duplicate save preserving the draft; repeated Save taps starting one save; failed save retaining/persisting input; and typing during an in-flight save retaining the newer draft. `lintDebug` and `assembleDebug` pass.
- The owner verified save confirmation, recent-items display, read-only detail, offline capture/read, a debounced draft surviving close/reopen, and a saved note surviving a non-destructive `adb install -r` update. The updated app cold-launched with `MainActivity` focused on 2026-09-13.

## Verified Milestone 3A work

- Supabase Free project setup is complete for one owner account. Public sign-ups are disabled. The Android application contains only the Supabase Project URL and publishable key through an ignored local properties file; the owner UUID remains in ignored backend configuration. No database password, service-role key, signing key, token, or personal content is tracked.
- The local FastAPI/Pydantic API verifies the caller's Supabase JWT against the project's JWKS and separately checks that its `sub` equals the configured owner UUID. It then calls Supabase PostgREST using the caller's bearer token plus the publishable key, so Postgres RLS applies; it does not use a privileged database or service credential.
- Supabase migrations create `text_notes` with a composite `(owner_id, client_note_id)` key, owner-only select/insert RLS policies, and a `security invoker` RPC. The RPC returns the existing record for the same stable local ID and immutable content, and rejects reuse of that ID with different content. Corrective migration `0002` qualifies `extensions.digest` for Supabase's extension schema without altering rows.
- Room migrated non-destructively from version 1 to 2, adding only local sync metadata. Existing note IDs and bodies remain intact. Room remains the UI source of truth: a local save starts as pending; explicit upload moves through uploading; only a matching authenticated cloud read-back marks it synced; failure leaves the local note readable and retryable.
- Backend contract tests passed (6/6), including missing-token rejection, non-owner rejection before cloud access, idempotent repeat upload/read-back, and immutable-ID conflict. Android `lintDebug`, `assembleDebug`, and focused instrumented persistence tests passed on the authorised Samsung SM-S918U1 (Android 16/API 36).
- The owner signed in on the phone, uploaded a text note, received **Cloud read-back verified**, repeated verification without creating a second `text_notes` row, closed/reopened the app, and confirmed the locally stored note and cloud-verified state remained. Earlier safe failures left the local note readable and retryable before the successful retry.
- FastAPI is **not deployed**. The verified 3A cloud path runs on the owner's laptop and reaches the phone through a temporary ADB reverse rule, so sign-in/upload/read-back testing requires the laptop API process to be running. Device-local capture and reading do not require the laptop or network.

## Verified local media and automatic-sync checkpoint

- The Room database migrated non-destructively to version 3. Existing text notes are mirrored into a typed `vault_items` table; new images are copied to private app storage before their durable metadata/outbox record is created, and public links are validated locally. Existing local text remains intact.
- Migration `0004_vault_items_and_private_originals.sql` was applied successfully in Supabase. It creates the private `justdump-originals` bucket, a 10 MB image limit, owner-only Storage policies, and owner-scoped idempotent metadata for images and links.
- WorkManager drains the durable local outbox only when Android reports a network. For media, it marks an item synced only after FastAPI stores it using the caller JWT under RLS and a matching authenticated metadata read-back succeeds. Failed items retain their local original and remain retryable through **Upload and verify**.
- Backend contract checks passed (16 tests), and Android `assembleDebug` and `lintDebug` passed. The owner installed the updated debug build on the Samsung device and confirmed an image/link upload reached **Cloud read-back verified**.
- FastAPI still runs locally on the laptop, so this test requires the laptop process, ADB reverse rule, and phone connection. This is cloud synchronisation during development, not a tested backup or recovery capability.

## Confirmed context

- JustDump is initially a single-user Android application.
- Initial formats are text, screenshots/images, and public article links; video links may be saved while full video processing and audio support are deferred.
- The initial physical test device is a Samsung Galaxy S23 Ultra (SM-S918U1), verified as Android 16/API 36 with Samsung One UI property `80500`.
- Milestone 1 uses application ID `io.github.preetham7cs.justdump`, minimum SDK 26, compile/target SDK 36, and standard debug signing.
- Development begins with a measurable retrieval baseline and introduces agentic behaviour only through evaluated increments.
- Milestone 3A uses explicit user-triggered uploads only. Automatic retry/background sync remains Milestone 3B; cloud storage is not described as a backup.
- **2026-11-15:** Continuous v1 delivery replaces separate milestone approvals. The owner authorises verified local commits and pushes to `origin/main`; no force push. Paid hosting/deployment remains deferred until the locally testable v1 is ready.
- **2026-11-15:** Up to $10/month is authorised for OpenAI API use. The owner approves OpenAI processing of their selected personal text, images/screenshots, and public links/extracted text for v1 ingestion and Q&A. LangSmith remains limited to redacted operational metadata until further approval.
- **2026-11-15:** Supabase remains the Auth/Postgres/pgvector/object-storage foundation and will also be evaluated for durable queues and scheduling. Its Edge Functions are TypeScript/Deno, so they do not directly host the existing Python FastAPI/LangGraph service. Render or another Python host remains a later decision; laptop-off hosted verification is deferred, not removed.

## Proposed directions requiring later agreement

- Kotlin/Jetpack Compose; Python/FastAPI/Pydantic; Supabase Postgres/pgvector and object storage; persistent Python worker; LangGraph; LangSmith or a justified alternative; Docker/GitHub Actions; signed APK.
- Capture-first home, with possible **Recents**, **My World**, and **Favourites** destinations.
- Provenance-bearing relationships stored in Postgres first, followed by graph-assisted retrieval evaluation.
- Task-based routing across deterministic logic, SLMs, open-weight/local inference, and hosted inference where measured value justifies it.

## Unresolved questions

- Detailed accessibility targets and a local scheduler beyond the current debounce.
- Final home/navigation/ask interaction and visual direction.
- Hosting/deployment topology, authentication configuration, persistent job/checkpoint technology, and worker environment.
- AI/embedding candidates, licences, privacy, local/laptop/on-device feasibility, quality/latency targets, fallbacks, and budget.
- Evaluation dataset/annotations, metrics, provisional thresholds, graph-traversal use cases, and dedicated graph-database evidence limits.
- Retention, deletion, export, backup/restore, recovery objectives, and trace/log redaction policy.
- Later document/audio/video scope, access rules, limits, and provider/local options.
- APK distribution and production signing-key custody.

Decisions should be made only when their milestone needs them, using current official evidence for time-sensitive provider/model claims.

## Current delivery state

The 3R retrieval baseline is committed and pushed. Migration 0003 was applied, and the deliberately synthetic `evals/synthetic/v1` corpus was uploaded through the authenticated API, then indexed explicitly. The fixed FTS/vector development and held-out runs completed; their redacted reports are ignored, while [the tracked results summary](../evals/synthetic/v1/results-summary.md) records measured Recall@3/MRR, citation validity, abstention, conflict failures, and the $0.013036 actual provider usage. A subsequent synthetic conflict refinement is verified locally and by a direct model contract check: conflicting dates cause a required dual-citation conflict answer, with a deterministic dual-citation fallback for malformed model output. Only explicit fixture IDs were indexed; existing personal notes were not selected automatically. Continuous delivery now proceeds with Android sharing, durable processing, owner-scoped retrieval, LangGraph Q&A, and phone verification. Hosted Python delivery remains deferred pending an explicit provider decision.

Render remains only a proposed API host. No hosted service or paid option has been enabled.
