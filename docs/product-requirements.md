# Product requirements

## Product goal

JustDump is a private, single-user knowledge application, initially for Android. It should feel effortless: open it and immediately have a place to drop, paste, type, upload, or share useful content. It must preserve that material reliably, organise it, and answer questions grounded in it with inspectable sources.

The project is also a serious learning vehicle for retrieval-augmented generation (RAG), structured knowledge, LangGraph orchestration, tool calling, evaluation, tracing, deployment, recovery, and maintenance. Development starts with a measurable retrieval baseline. Agentic and graph-assisted behaviour is introduced only through evaluated increments.

## User journeys

### Capture directly

1. The owner opens JustDump into a capture-first space rather than a technical dashboard.
2. They type or paste text, add a link, or choose an image/screenshot.
3. The app confirms a durable local save immediately and shows unobtrusive sync and processing status.
4. If offline, the capture remains usable and uploads automatically after connectivity returns.

### Share from another app

1. The owner selects text, an image, or a URL in another Android app.
2. They choose **Share -> JustDump**.
3. JustDump saves the content locally and makes clear whether it is merely saved, uploaded, processed, or ready to search.

### Browse and explore

1. The owner browses recent or favourite items and opens an item's original content and source metadata.
2. They can inspect uploading, processing, ready, failed, and offline states without seeing model or pipeline internals.
3. They may explore people, places, topics, activities, and connections derived from their knowledge. This experience, provisionally called **My World**, need not be a complex node-link graph.

### Ask

1. The owner asks a question about saved material from an intuitive entry point.
2. JustDump retrieves relevant evidence and answers from it.
3. Source references open the supporting saved item and relevant passage, image, or later timestamp where possible.
4. When evidence is insufficient or conflicting, the answer says so rather than inventing support.

## Initial scope

- Authenticated, single-user access.
- Direct and Android share-sheet capture of text, screenshots/images, and public article links.
- Save video links as items; do not promise transcription or full video processing initially. Audio capture and processing remain long-term capabilities.
- Durable local capture, offline upload queue, bounded automatic retries, and visible sync state.
- Extraction and organisation with understandable processing state and partial-failure messages.
- Browse saved items, view originals, and optionally mark favourites.
- Ask questions with references to supporting saved items.
- Preserve user data across Android app updates and backend deployments.

Saving a link confirms that the URL item is durable; it does **not** mean the linked page was accessible or extracted successfully. Those are separate processing outcomes.

## Long-term capabilities

- Capture and process documents, audio files, and video files in addition to initial formats.
- Transcription, media understanding, and references to relevant timestamps.
- Structured exploration of entities and relationships across saved knowledge.
- Carefully evaluated model routing and bounded agentic retrieval.
- Export, recovery, and mature maintenance workflows.

Media support will depend on content access, platform restrictions, transcript availability, file size and duration limits, privacy, processing cost, and clear failure handling. Providers and universal platform coverage are not promised.

## Deferred or out of scope for the initial release

- Multi-user sharing, collaboration, and public publishing.
- Full document, audio, or video processing.
- Private or paywalled link ingestion requiring third-party credentials.
- Browser and desktop capture clients.
- Autonomous agents that take external actions.
- Production-scale availability commitments beyond one owner's use.

## Experience requirements

- Adding content is the primary home-screen interaction.
- Navigation labels such as **Recents**, **My World**, and **Favourites** are proposed, not confirmed; their purposes are described in `ux-principles.md`.
- Empty, uploading, processing, failed, offline, and ready states must be designed and understandable.
- Save confirmation must be reassuring; routine processing status must not interrupt capture.
- Original content and evidence behind answers must remain inspectable.
- Model names, embeddings, graph internals, token usage, and processing implementation details stay out of normal user flows.

## Reliability and privacy requirements

- A successful local save must survive app restart, process death, and loss of connectivity.
- Uploads and processing must be idempotent; retries must not create duplicate logical items or derived records.
- Content hashes should support duplicate detection without silently discarding intentionally repeated saves.
- Sync and processing states must be observable, with bounded retries and actionable messages for terminal or partial failures.
- Saved records and originals must use stable identifiers and durable migrations so updates do not silently discard data.
- Authentication must protect all owner data; backend access must not rely only on a hidden client value.
- Deployed network traffic must use encrypted transport.
- Logs, traces, evaluation datasets, and source control must not expose credentials or personal content by default.
- Deletion must cover the original and its associated derived data, indexes, relationships, and citations under a documented policy.
- Backup, export, restore, and recovery behaviour must be defined before the system holds irreplaceable data.
- Extracted facts, relationships, summaries, and answers must retain provenance to their source item and relevant evidence.
- Processing configuration must be versioned so changed extraction, prompts, models, and embeddings can be evaluated and selectively reprocessed.
- Expensive work must have explicit size, attempt, execution, token, and cost limits.

## Initial release acceptance criteria

The initial scope is accepted only when all of the following are demonstrated on the owner's physical test device and the selected deployed environment:

- The owner can authenticate and another account cannot read their data.
- Text, a screenshot/image, and a public article URL can each be captured directly and through supported Android share intents.
- A capture made offline remains after app restart and uploads automatically when connectivity returns.
- Repeated upload or processing attempts do not create duplicate logical items.
- Every item displays a truthful state; a deliberately induced failure displays a useful message and safe retry path.
- A saved but unextractable link remains visible and is not labelled ready for search.
- Saved items remain available after an app update and a backend redeployment/migration rehearsal.
- Browse opens retained original content, and answer references open supporting items.
- A versioned evaluation set measures extraction, retrieval, answers, citations, and abstention before agentic routing is introduced.
- Retrieval and grounded answers meet thresholds agreed after the baseline exists.
- Automated software tests cover critical data, authentication, sync, processing, and migration behaviour; AI quality evaluations separately cover probabilistic outcomes.

Numeric quality, latency, storage, cost, and recovery thresholds are provisional until relevant baselines exist.

## Confirmed context and unresolved decisions

- **Confirmed:** Initial physical test device is a Samsung Galaxy S23 Ultra; user-reported software is Android 16 with One UI 8.5. This does not determine the minimum supported Android version.
- **Unresolved:** Minimum Android version, target device range, accessibility targets, and final navigation language.
- **Unresolved:** Hosting, models, budget, retention, deletion, export, backup, and recovery targets.
