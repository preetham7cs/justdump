# Progress

Last updated: 2026-09-13

## Current state

Milestones 0 and 1 are complete. The minimal Android app has been built, linted, debug-signed, installed, and launched on the physical test phone. Automated device inspection found the expected `JustDump` text in the focused activity and no matching fatal crash. The owner then confirmed that the welcome screen was visible and that JustDump reopened successfully from its launcher icon.

## Verified Milestone 1 work

- Android Studio Quail 4 is installed; its bundled runtime is OpenJDK 25.0.3.
- Android SDK Platform 36, Build Tools 36.0.0, and Platform Tools/ADB 37.0.1 are installed.
- Wireless ADB detected and authorised the Samsung SM-S918U1. Device properties report Android 16, API 36, and Samsung One UI property `80500` (One UI 8.5).
- `apps/android/` contains one `app` module using Kotlin and Jetpack Compose, application ID `io.github.preetham7cs.justdump`, minimum SDK 26, compile SDK 36, and target SDK 36.
- The pinned build uses Android Gradle Plugin 9.4.0, Gradle wrapper 9.6.0, Kotlin/Compose compiler 2.3.21, Compose BOM 2026.04.01, and Activity Compose 1.11.0.
- `gradlew.bat clean lintDebug assembleDebug` completed successfully. Final lint reported zero errors and six intentional version-availability warnings caused by the approved API 36/toolchain pins. The generated debug APK verified with an `Android Debug` certificate.
- ADB streamed installation succeeded. A cold launch completed, Android reported `MainActivity` focused, the live UI hierarchy contained `JustDump`, and the crash buffer contained no matching fatal exception.
- The owner visually confirmed the `JustDump` welcome screen and successfully reopened the app from its launcher icon on 2026-09-13.

Generated APKs, build reports, Gradle caches, local IDE state, SDK paths, and debug signing material are excluded from Git. No app feature beyond the welcome screen has been implemented.

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

- Local database/scheduler and detailed accessibility targets for the first capture feature.
- Final home/navigation/ask interaction and visual direction.
- Hosting/deployment topology, authentication configuration, persistent job/checkpoint technology, and worker environment.
- AI/embedding candidates, licences, privacy, local/laptop/on-device feasibility, quality/latency targets, fallbacks, and budget.
- Evaluation dataset/annotations, metrics, provisional thresholds, graph-traversal use cases, and dedicated graph-database evidence limits.
- Retention, deletion, export, backup/restore, recovery objectives, and trace/log redaction policy.
- Later document/audio/video scope, access rules, limits, and provider/local options.
- APK distribution and production signing-key custody.

Decisions should be made only when their milestone needs them, using current official evidence for time-sensitive provider/model claims.

## Next proposed milestone

Milestone 2 is the capture-first local text vault described in `roadmap.md`. Its design and implementation have not started.

Before Milestone 2, explain the relevant concepts and options, recommend a tightly bounded scope, and obtain owner agreement.
