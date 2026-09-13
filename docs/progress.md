# Progress

Last updated: 2026-09-13

## Current state

Milestones 0, 1, and 2 are complete. Milestone 3A is awaiting Supabase setup: a single-owner, explicit cloud upload/read-back experiment for text is approved. Supabase Free, Supabase Auth email/password, Supabase Postgres, FastAPI, Pydantic, Room as the Android UI source of truth, and deferred automatic background sync are agreed directions. No Supabase project, owner account, hosted API, credentials, backend, or Android cloud feature has yet been created or verified.

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

## Confirmed context

- JustDump is initially a single-user Android application.
- Initial formats are text, screenshots/images, and public article links; video links may be saved while full video processing and audio support are deferred.
- The initial physical test device is a Samsung Galaxy S23 Ultra (SM-S918U1), verified as Android 16/API 36 with Samsung One UI property `80500`.
- Milestone 1 uses application ID `io.github.preetham7cs.justdump`, minimum SDK 26, compile/target SDK 36, and standard debug signing.
- Development begins with a measurable retrieval baseline and introduces agentic behaviour only through evaluated increments.

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

## Current milestone

Milestone 3A is limited to authenticated explicit text upload and authenticated cloud read-back for the single owner. Existing local notes must survive; automatic background sync is Milestone 3B, and AI features are out of scope.

Prerequisite: the owner must create and configure the Supabase Free development project before implementation can connect to it. Render Free is the lowest-cost proposed API host but has not been enabled. No paid service is approved.

The earliest proposed AI work is Milestone 3R, a text-only backend retrieval and evaluation baseline after 3A. It uses explicitly uploaded notes and does not require UI polish, additional formats, or Milestone 3B automatic sync.
