# Evaluation strategy

Status: **proposed**. Numeric thresholds below are intentionally not fixed until a baseline reveals realistic performance and error distributions.

## Purpose

Evaluation answers whether a change makes JustDump more useful and trustworthy. It starts at component level, then checks the complete capture-to-answer path. Every observed production or manual-review failure should become a privacy-safe regression example when possible.

Software tests and AI evaluations are different:

- **Software tests** check deterministic contracts such as authentication, migrations, idempotency, state transitions, deletion, schema validation, and citation-link navigation.
- **AI quality evaluations** measure probabilistic extraction, retrieval, routing, grounded answers, and abstention against reviewed examples.

Both are required; one cannot substitute for the other.

## Starting datasets

Create a small synthetic or licensed, non-personal corpus covering initial formats, varied lengths, duplicates, named entities, relationships, conflicting statements, unanswerable questions, and malicious instructions embedded in saved content. A person manually reviews the originals, evidence spans, expected entities/relationships, relevant items/passages, and answer claims.

Split examples before tuning:

- **Development set:** visible for iteration, diagnosis, and prompt/index changes.
- **Held-out set:** used less frequently to check generalisation and guard against tailoring to known cases.

Version dataset contents, split membership, annotations, processing code, prompts, models, embeddings, index configuration, graph schema, routing policy, and experiment parameters. Personal vault data must not silently become an evaluation dataset.

## Evaluation layers

### 1. Extraction

Measure text/OCR extraction where ground truth is available and precision, recall, and F1 for structured entities and relationships. Review evidence-span accuracy, entity resolution, duplicates, aliases, conflicting facts, missing facts, and changes after reprocessing. A relationship is not correct without supported provenance.

### 2. Retrieval baseline

Measure keyword and vector retrieval independently, then a simple hybrid if justified. Report at least Recall@k (whether required evidence appears in the top *k*) and a ranking measure such as reciprocal rank or nDCG. Break results down by exact-term, paraphrase, filter, time, modality, and conflict cases. Record latency and index/version details.

### 3. Graph-assisted retrieval

On the same corpus and questions, add candidates reached through provenance-bearing entity/relationship traversal. Compare retrieval quality, unique useful evidence found, incorrect expansion, latency, storage, processing cost, and operational complexity against the baseline.

A dedicated graph database is justified only if:

1. graph-assisted retrieval gives a material held-out improvement on agreed use cases;
2. the same useful traversals in Postgres fail agreed performance, maintainability, or scale criteria; and
3. a trial demonstrates acceptable provenance, deletion consistency, migration/rollback, backup/restore, monitoring, and total cost.

All numeric cut-offs remain provisional until baseline measurements and representative query shapes exist.

### 4. Answers and citations

Score answer correctness, claim-level groundedness, completeness, citation precision/recall, and whether each citation opens the right item/evidence location. Include conflicting sources and questions that require synthesis. Human review is the reference for important errors.

### 5. Abstention and hostile content

Measure whether the system refuses or qualifies answers when evidence is missing, weak, inaccessible, or conflicting. Include saved webpages/notes containing prompt injection that tells the system to ignore rules, reveal data, call tools, or treat embedded text as instructions. Saved content is evidence, never trusted control input.

### 6. LangGraph, tools, and routing

Check structured state/output schemas, correct tool selection, route appropriateness, fallback behaviour, checkpoint resume, retry bounds, evidence-sufficiency decisions, and guaranteed termination. Compare each agentic refinement against the simpler flow; count unnecessary tool/model calls and route escalations.

### 7. Operational measures

Track end-to-end and per-stage latency, token/usage measures, estimated or billed cost, queue delay, attempts, processing failures, partial failures, recovery success, index growth, and reprocessing work avoided. Define budgets before enabling expensive paths.

## Methods

- **Deterministic checks:** exact schemas, expected source IDs, state transitions, invariants, limits, and known rankings where appropriate.
- **Human review:** ambiguous extraction, factual correctness, usefulness, conflicts, citations, and high-severity failures.
- **Calibrated LLM judges:** scalable comparisons only after agreement with human labels is measured; pin judge prompt/model versions and retain human spot checks.
- **Component evaluations:** isolate extraction, resolution, retrieval, reranking, routing, and generation to locate regressions.
- **End-to-end evaluations:** confirm that the integrated answer and source experience works with realistic failures.

## LangSmith and privacy-aware observability

LangSmith is proposed for trace inspection and experiment comparison; a justified alternative may be selected. Traces should capture node/tool names, route reasons, timings, status, version identifiers, token/usage measures, and redacted error metadata. Raw personal content and credentials are excluded or redacted by default, with retention and access rules agreed before use.

Experiment reports should link configuration, aggregate and per-example results, regressions, trace samples, limitations, and the keep/reject decision. Tracing success is not proof of quality; it supplies evidence for evaluation and debugging.

## Provisional acceptance approach

Before a milestone begins, agree severity-based gates and provisional numeric thresholds using the current baseline. At minimum:

- no known cross-user access, data-loss, unbounded-loop, or uncited fabricated-answer failure;
- retrieval meets agreed Recall@k and ranking targets on development and held-out sets;
- answers meet agreed groundedness, citation, and abstention targets;
- graph or agentic variants show a meaningful held-out benefit that justifies added latency, cost, and complexity;
- execution, retries, tokens, latency, and cost remain within agreed bounds.

Thresholds must be labelled provisional until enough reviewed examples exist, then recorded with the dataset and experiment version rather than silently changed.
