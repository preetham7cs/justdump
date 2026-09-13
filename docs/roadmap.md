# Roadmap

Only one milestone is implemented at a time, after owner agreement. Each is a proposed boundary and may be split further. Demonstration, relevant tests/evaluations, learning notes, and progress updates are part of completion.

## 0. Product foundation (complete)

- **Outcome:** Requirements, UX direction, proposed architecture/model/evaluation strategies, working agreement, roadmap, progress log, and learning template tell one consistent story.
- **Learning objective:** Separate behavioural requirements, architectural proposals, unresolved decisions, evaluation evidence, and verified progress.
- **Prerequisites:** Product brief and documentation review request.
- **Verification:** Review links, terminology, scope, decision labels, and unresolved questions; confirm no application scaffold or dependency exists.
- **Completion criteria:** Documents make no implementation claims, cover the stated product and AI-learning vision, and are accepted by the owner.

## 1. Android app on the phone (complete)

- **Outcome:** A minimal JustDump app builds, installs, opens, and shows a `JustDump` welcome screen on the Samsung Galaxy S23 Ultra.
- **Learning objective:** Android project structure, Gradle build variants, Compose UI, APK creation, device installation, and startup logs.
- **Prerequisites:** Agree SDK/tool versions, application ID, minimum Android version independently of the test phone, and safe development signing; confirm phone connection method.
- **Verification:** Run lint and build checks, verify debug signing, install the development APK, cold-open it, reopen it from the launcher icon, and inspect logs for startup failures.
- **Completion criteria:** The owner completes the install/open steps; observed device/software details and commands are recorded; no capture, backend, or AI behaviour is implied.

## 2. Capture-first local text vault (complete)

- **Outcome:** The owner can immediately type/paste, save, browse, and read text locally, with one persistent capture draft; empty and ready states follow the UX principles. Favourites are deferred.
- **Learning objective:** Compose state versus persisted state, navigation, local modelling, repositories, migrations, accessibility, and Android tests.
- **Prerequisites:** Milestone 1; agree local database, minimal item schema, and first UX slice.
- **Verification:** Run focused repository and ViewModel device tests for persistence, failed saves, repeated Save taps, and draft/save coordination. On the phone, save/read notes, restart/reopen, test offline capture, and verify a draft after waiting for its documented debounce interval. Reinstall the debug app with `-r` over a saved note and confirm it remains.
- **Completion criteria:** Notes remain readable after the agreed checks, the owner confirms the flow on the phone, and the UI does not expose unimplemented processing, cloud, or technical controls.

## 3. Authenticated backend and safe offline sync

- **Outcome:** The authenticated owner syncs text items with durable offline queuing, bounded retries, and clear offline/uploading/failed states.
- **Learning objective:** API contracts, authentication, Pydantic validation, migrations, idempotency, content hashes, background scheduling, and sync state machines.
- **Prerequisites:** Milestone 2; decide hosting, auth flow, Postgres setup, secrets handling, scheduler, and test environments.
- **Verification:** Exercise authorised/unauthorised tests, repeat an upload, interrupt connectivity and app execution, then confirm eventual single-item sync.
- **Completion criteria:** Only the owner accesses data; intentional saves are preserved; retry creates no duplicate logical record; status and recovery are understandable.

## 4. Android sharing, links, and images

- **Outcome:** Text, public URLs, images, and screenshots can be captured directly and through **Share -> JustDump**; originals are retained separately.
- **Learning objective:** Android intents, content URIs, permissions, object storage, modality metadata, partial success, and resilient transfer.
- **Prerequisites:** Milestone 3; agree supported MIME types, file limits, storage rules, and link/item status semantics.
- **Verification:** Share each type from representative apps online/offline; restart/reconnect; revoke temporary URI access; induce retryable and terminal failures.
- **Completion criteria:** Captures survive and sync, URL-save status does not imply page extraction, failures preserve originals, and video links are stored without promising media processing.

## 5. Versioned persistent processing

- **Outcome:** Text, images, and public articles move through durable extraction, normalisation, chunking, and indexing stages with provenance and truthful partial-failure states.
- **Learning objective:** Jobs, workers, leases, OCR/web extraction, idempotency, versioned derived data, bounded work, selective reprocessing, deletion, and observability.
- **Prerequisites:** Milestone 4; choose job technology, extractors, worker runtime, processing limits, deletion rules, and privacy-safe logging.
- **Verification:** Crash/restart a worker, repeat/partially fail jobs, change a processing version, delete test items, and verify provenance and absence of orphaned derived data.
- **Completion criteria:** Work resumes safely; unchanged expensive stages are reusable; every derived record links to evidence and a processing version; failure messages are useful.

## 6. Reproducible retrieval baseline

- **Outcome:** A versioned development/held-out corpus measures keyword, vector, filter, and simple hybrid retrieval before graph-assisted or agentic changes.
- **Learning objective:** Relevance judgements, chunking, embeddings, full-text search, Recall@k, ranking metrics, latency/cost measurement, and regression analysis.
- **Prerequisites:** Milestone 5; approve non-personal dataset policy, candidate embedding verification, metrics, provisional thresholds, and budget limits.
- **Verification:** Run repeatable component evaluations with per-query results and versioned configurations; manually review failures.
- **Completion criteria:** Baseline results reproduce, held-out data remains untuned, failures become regression cases, and thresholds for the next milestone are agreed.

## 7. Grounded questions with sources

- **Outcome:** The Android app answers questions from retrieved evidence; references open supporting items/locations and weak or conflicting evidence causes qualification or abstention.
- **Learning objective:** RAG, evidence boundaries, prompt-injection resistance, citation mapping, groundedness, abstention, and end-to-end evaluation.
- **Prerequisites:** Milestone 6 meets agreed retrieval gates; verify an answer-model candidate and rubric under the model strategy.
- **Verification:** Evaluate supported, conflicting, unanswerable, and saved-content prompt-injection cases; manually test asking and source inspection on the phone.
- **Completion criteria:** Correctness, groundedness, citation, abstention, latency, and cost meet provisional agreed gates with no known severe trust failure.

## 8. LangGraph orchestration and bounded tools

- **Outcome:** Separate ingestion and question flows use typed LangGraph state; one bounded query-refinement or retrieval-tool decision is compared with the simpler flow.
- **Learning objective:** Nodes, edges, conditional routing, structured outputs, tool contracts, checkpointing/recovery, streaming, evidence sufficiency, retries, and termination limits.
- **Prerequisites:** Milestone 7; define the behaviour hypothesis, trace/redaction policy, execution/token/cost bounds, fallback, and rollback path.
- **Verification:** Resume an interrupted run; force tool and model errors; inspect privacy-safe traces; compare quality, calls, termination, latency, and cost on the same held-out set.
- **Completion criteria:** The addition remains only if it meets agreed improvement gates; all paths terminate within bounds; otherwise the simpler flow remains and the result is recorded.

## 9. Structured knowledge and graph-assisted retrieval

- **Outcome:** Versioned, provenance-bearing entities and relationships in Postgres support a graph-assisted retrieval experiment and a useful first **My World** exploration.
- **Learning objective:** Structured extraction, entity resolution, conflicts, temporal facts, relationship traversal, graph retrieval, provenance, and knowledge graph versus execution graph.
- **Prerequisites:** Milestone 8; agree entity schema, annotation/evaluation set, merge/split and deletion rules, useful relationship queries, and UX slice.
- **Verification:** Measure entity/relationship extraction and compare graph-assisted retrieval with the milestone 6 baseline on development and held-out sets; test duplicates, conflicts, reprocessing, and deletion.
- **Completion criteria:** Provenance and lifecycle rules hold; the owner finds the exploration useful; graph assistance is retained only if it improves agreed cases. A dedicated graph database remains unnecessary unless the evidence criteria in `evaluation-strategy.md` are met.

## 10. Evaluated task-based model routing

- **Outcome:** One bounded task routes between a deterministic rule, SLM, open-weight/local option, or hosted option according to a versioned policy with a clear fallback.
- **Learning objective:** Open-source versus open-weight, local/hosted/on-device inference, structured routing, privacy, capability verification, calibration, and quality/latency/cost trade-offs.
- **Prerequisites:** Relevant official model/licence/pricing research; hardware assessment; approved privacy policy, candidates, task dataset, limits, and fallback.
- **Verification:** Compare routes on the same held-out examples; inspect logged reason codes, failures, fallback, latency, resource use, tokens, and cost.
- **Completion criteria:** Routing beats a single simpler route under agreed trade-offs and terminates within limits; otherwise keep the simpler policy and record why.

## 11. Long-form media experiment

- **Outcome:** A tightly bounded document, audio, or video ingestion experiment determines whether and how one deferred modality should enter scope.
- **Learning objective:** Access restrictions, transcripts, file/duration limits, hosted open models, local options, multimodal provenance, processing cost, and recovery.
- **Prerequisites:** Research current low-cost services and local/hosted candidates; agree one modality/source class, non-personal dataset, limits, privacy, and budget.
- **Verification:** Test accessible, inaccessible, oversized, partial, and failed inputs; measure extraction quality, timestamps, latency, resource use, and cost.
- **Completion criteria:** Evidence supports a scoped product proposal with explicit unsupported cases; no provider or universal platform coverage is assumed.

## 12. Data resilience and repeatable delivery

- **Outcome:** Updates, migrations, export, backups/restores, backend redeployments, monitoring, and signed APK delivery follow tested runbooks.
- **Learning objective:** Containers, CI, signing, compatibility, recovery drills, failure monitoring, rollback, budgets, and ongoing maintenance.
- **Prerequisites:** Decide CI, hosting, retention, backup destination, recovery targets, release channel, alerting, budget, and signing-key custody.
- **Verification:** Rehearse app upgrade, migration/redeployment, export, deletion, backup restore, worker recovery, rollback, and signed APK installation using a test corpus.
- **Completion criteria:** Checks/runbooks pass within agreed recovery targets, data survives intended changes, secrets stay outside source control, and owner approval precedes deployment or push.
