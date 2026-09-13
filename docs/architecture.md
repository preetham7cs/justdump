# Architecture

This is a proposed architecture, not an implemented system. A choice marked **preferred** is a direction to evaluate, not a confirmed dependency. Choices become **confirmed** only after owner agreement and milestone evidence.

The implemented Android foundation is one native application module using Kotlin and Jetpack Compose. Its confirmed identifiers are application ID `io.github.preetham7cs.justdump`, minimum SDK 26, and compile/target SDK 36. Standard debug signing is confirmed for development only; no product capabilities or later architecture components are implemented yet.

## Proposed components

| Component | Proposed technology | Responsibility |
| --- | --- | --- |
| Android client | Kotlin and Jetpack Compose | Capture/share intents, durable local save, sync state, browsing, questions, and source navigation. Local database and scheduler are unresolved. |
| HTTP API | Python, FastAPI, and Pydantic | Authenticate, validate versioned contracts, manage items/questions, and expose truthful state. |
| Primary data store | Supabase Postgres with pgvector | Proposed home for owner data, metadata, extracted content, chunks, vectors, provenance, relationships, and migrations. |
| Object storage | Supabase object storage | Proposed store for originals and binary payloads, separate from derived representations. |
| Persistent job system | Unresolved | Durably enqueue processing, lease work, bound retries, and record partial/terminal failures. |
| Python worker | Framework unresolved | Execute versioned extraction and indexing steps idempotently and report status. |
| Retrieval and answer service | Deterministic baseline first; LangGraph later | Retrieve evidence, assess sufficiency, generate grounded answers, and map citations to originals. |
| Evaluation and observability | LangSmith proposed, with local automated evaluation | Compare experiments and inspect redacted traces; a justified alternative remains possible. |
| Delivery | Docker, GitHub Actions, and a signed APK are proposed | Reproducible checks, migrations, backend packaging, and Android artifacts. Hosting is unresolved. |

## Content lifecycle

`capture -> durable local save -> queued upload -> canonical save -> queued processing -> extraction -> normalisation -> chunking where appropriate -> indexing -> retrieval`

1. The Android client creates a stable item identifier and stores the original or a durable local reference before confirming the save.
2. Background sync uploads with an idempotency identity. A content hash can flag likely duplicates, but product policy determines whether two intentional saves remain distinct.
3. The API stores the original separately from derived data and schedules a persistent job. Saving a URL and extracting its page are different states.
4. A worker performs modality-appropriate extraction: text parsing, webpage extraction, or OCR/image understanding initially; document parsing and audio/video transcription later.
5. Normalised output may include text, summaries, metadata, entities, relationships, and chunks. Every derived record carries its source location and processing-version provenance.
6. Indexing builds only the required full-text, vector, filter, or relationship representations. Completed stages are reusable so reprocessing does not repeat unchanged expensive work.
7. Retrieval returns evidence references that answer generation can preserve as citations.

Every stage needs explicit state, bounded attempts, idempotency, and independent error details. Partial results may remain useful, but `ready` must state what is ready. Size, duration, execution, token, and cost limits are unresolved and must be set before expensive processing.

## Proposed provenance and versioning

- `Item`: owner, stable capture identity, modality, capture metadata, original reference, content hash, and sync/processing summary.
- `Original`: immutable uploaded bytes or captured text/URL plus source metadata.
- `ProcessingRun`: extractor, prompt, model, code, and configuration versions; stage outcomes; timestamps; and errors.
- `DerivedContent`: versioned extracted text, OCR, transcript, summary, or structured metadata tied to an original and processing run.
- `EvidenceSpan`: a passage, page/region, or timestamp range within derived/original content.
- `Entity` and `Relationship`: canonical or unresolved concepts with confidence and provenance to one or more evidence spans.
- `Chunk` and `IndexEntry`: retrievable units tied to exact derived-content and embedding/index versions.
- `Conversation`, `Message`, and `Citation`: interaction history and response-to-evidence links.
- `Job`: durable work instruction, dependency/stage, attempts, lease, status, and failure detail.

Reprocessing should create or replace a derived version atomically, then retire obsolete index entries. Deleting an item must remove or tombstone originals, derived versions, index entries, provenance edges, and unsupported relationships according to an agreed policy. Shared entities require reference-aware deletion rather than blind cascading.

Entity resolution needs explicit handling for aliases, duplicate candidates, merges/splits, conflicting facts, confidence, time context, and extraction changes. Extracted claims are hypotheses linked to evidence, not unquestionable truth.

## Four kinds of state

| State | Meaning | Expected lifetime | Proposed home |
| --- | --- | --- | --- |
| Saved knowledge | Owner-controlled originals and derived knowledge used as evidence. | Until deletion under an agreed policy. | Postgres/pgvector and object storage, with local capture state while syncing. |
| Conversation history | Questions, responses, and citations. It can provide conversational context but is not automatically evidence. | Retention unresolved. | Postgres; optional device cache. |
| LangGraph checkpoints | Typed execution state for an ingestion or question run, including node outputs, routing, tool results, budgets, and errors. | Run-scoped or retained briefly for recovery/debugging. | LangGraph-compatible checkpointer; unresolved. |
| Background job state | Durable coordination state: stage, lease, attempts, dependencies, and failure classification. It is not knowledge or conversation. | Through completion plus an operational window. | Persistent job store/queue; unresolved. |

A **knowledge graph** represents domain entities and relationships found in saved content. A **LangGraph execution graph** represents control flow through processing or question-answering steps. They solve different problems and neither implies the other.

## Retrieval design

Each retrieval signal has a distinct role:

- **Keyword/full-text:** exact names, phrases, identifiers, and rare terms; transparent and inexpensive baseline behaviour.
- **Vector/semantic:** conceptually similar passages when wording differs; dependent on embedding quality and versioning.
- **Structured filters:** constrain by type, date, source, entity, processing state, or other metadata before/after ranking.
- **Entity/relationship traversal:** follow supported connections, such as a person linked to a place or activity, while retaining evidence provenance.
- **Hybrid retrieval and optional reranking:** combine candidates, deduplicate them, and improve ordering only when evaluation justifies added latency/cost.
- **Grounded answer generation:** use the selected evidence, cite it, expose conflicts, and abstain when evidence is insufficient.

The first baseline should combine simple full-text and vector retrieval as separately measurable signals, with filters where required. A later graph-assisted experiment should use the same questions and corpus, adding relationship-derived candidates. See `evaluation-strategy.md`.

### Relationship storage decision

Graph-assisted retrieval is the **preferred direction**, but a dedicated graph database is **unresolved**.

| Concern | Relationships in Postgres alongside pgvector | Dedicated graph database |
| --- | --- | --- |
| Query fit | Adequate for known, bounded traversals; recursive SQL can become awkward. | Natural for variable-depth traversal and graph algorithms. |
| Operations and cost | One transactional system, backup path, permission model, and bill to manage. | Another service, credentials, monitoring, backups, and failure mode. |
| Provenance/deletion | Joins and transactions can keep evidence and lifecycle rules close to items. | Cross-store identity and deletion consistency need explicit coordination. |
| Migration | Starts simple; later export requires stable IDs and portable edge schemas. | Earlier graph-specific modelling may increase lock-in and dual-write migration work. |
| Scale/performance | Must be measured on representative relationship queries. | May outperform complex traversals, but only evidence can justify it. |

Start by representing versioned, provenance-bearing entities and edges in Postgres. Consider a dedicated graph database only if the held-out graph-assisted evaluation improves useful retrieval and Postgres then fails agreed traversal latency, query complexity, scale, or maintainability criteria. Any trial must also demonstrate reliable sync, deletion, backup/restore, and rollback; novelty alone is not evidence.

## LangGraph flow design

Ingestion and questions are separate graphs/workflows even if they share tools.

### Ingestion flow

Deterministic steps should validate content, compute hashes, store originals, select allowed processors, normalise outputs, version results, and update indexes. Conditional routing may choose modality-specific extraction or stop on limits. Checkpoints should support safe recovery without replacing the persistent job record.

### Question-answering flow

A typed state can contain the question, filters, retrieval attempts, candidates, evidence assessment, route/model decisions, budgets, answer, citations, and errors. Deterministic nodes should enforce schemas, budgets, citation mapping, and termination. Bounded agentic decisions may refine a query, select a retrieval tool, or retry once when a measurable sufficiency check fails.

Structured model outputs and narrow tool contracts are preferred over free-form control signals. Streaming may expose answer progress when useful, but must not present unsupported text as final. Every run needs execution, retrieval-attempt, token, latency, and cost limits plus explicit fallback/abstention behaviour. A multi-agent architecture is not proposed.

## Model strategy

Model selection and task routing are specified in `model-strategy.md`. Hosted APIs, open-source software, open-weight models, small language models, local/laptop inference, hosted inference, and on-device inference remain options to evaluate. No specific model or device capability is confirmed.

## Decisions still to make

- Local database/scheduler and detailed Android accessibility targets.
- Hosting and deployment topology; Supabase project/authentication configuration.
- Job store, worker execution environment, checkpoints, and recovery targets.
- Model and embedding candidates, execution location, hardware feasibility, licences, privacy, budgets, and fallbacks.
- Evaluation corpus, baseline and held-out thresholds, acceptable latency/cost, and graph-database evidence criteria.
- Retention, deletion, export, backup, restore, and observability redaction policies.
- APK distribution and production signing-key custody.
