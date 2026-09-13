# Progress

Last updated: 2026-09-12

## Current state

Milestone 0, product foundation, has been expanded and is prepared for owner review. Implementation has not started. No dependencies, application scaffolds, infrastructure, external services, deployments, automated tests, traces, experiments, or CI workflows have been created or verified.

## Documentation completed for review

- Product behaviour now covers capture-first UX, truthful content states, initial versus long-term formats, provenance, deletion/recovery, and the physical test device.
- Proposed architecture now covers the processing lifecycle, versioned derived data, separate ingestion/question flows, retrieval modes, and the distinction between a knowledge graph and LangGraph.
- Graph-assisted retrieval is recorded as a preferred direction to evaluate, beginning with relationships in Postgres; a dedicated graph database has explicit evidence gates rather than being assumed.
- Focused UX, model, and evaluation strategies now define proposed approaches without selecting providers, models, budgets, or unsupported device capabilities.
- The roadmap introduces RAG, LangGraph, structured knowledge, graph-assisted search, task routing, later media research, and operations through small measurable milestones.
- The learning template records reproducibility details and decision status.

These are documentation drafts, not evidence of working behaviour. Milestone 0 remains incomplete until the owner accepts it.

## Confirmed context

- JustDump is initially a single-user Android application.
- Initial formats are text, screenshots/images, and public article links; video links may be saved while full video processing and audio support are deferred.
- The initial physical test device is a Samsung Galaxy S23 Ultra with user-reported Android 16 and One UI 8.5.
- The first implementation milestone remains a minimal Android app installed and opened on that phone.
- Development begins with a measurable retrieval baseline and introduces agentic behaviour only through evaluated increments.

## Proposed directions requiring later agreement

- Kotlin/Jetpack Compose; Python/FastAPI/Pydantic; Supabase Postgres/pgvector and object storage; persistent Python worker; LangGraph; LangSmith or a justified alternative; Docker/GitHub Actions; signed APK.
- Capture-first home, with possible **Recents**, **My World**, and **Favourites** destinations.
- Provenance-bearing relationships stored in Postgres first, followed by graph-assisted retrieval evaluation.
- Task-based routing across deterministic logic, SLMs, open-weight/local inference, and hosted inference where measured value justifies it.

## Unresolved questions

- Android application ID, SDK/tool versions, minimum supported Android version, local database/scheduler, accessibility targets, development signing, and phone connection workflow.
- Final home/navigation/ask interaction and visual direction.
- Hosting/deployment topology, authentication configuration, persistent job/checkpoint technology, and worker environment.
- AI/embedding candidates, licences, privacy, local/laptop/on-device feasibility, quality/latency targets, fallbacks, and budget.
- Evaluation dataset/annotations, metrics, provisional thresholds, graph-traversal use cases, and dedicated graph-database evidence limits.
- Retention, deletion, export, backup/restore, recovery objectives, and trace/log redaction policy.
- Later document/audio/video scope, access rules, limits, and provider/local options.
- APK distribution and production signing-key custody.

Decisions should be made only when their milestone needs them, using current official evidence for time-sensitive provider/model claims.

## Next proposed milestone

Milestone 1: build a minimal Android app, install it on the Samsung Galaxy S23 Ultra, and open it successfully.

Before implementation, discuss Android project structure, Compose and Gradle roles, SDK/application-ID options, minimum-version trade-offs, signing, and phone installation, then make a recommendation. Implementation starts only after owner agreement.
