# Model strategy

Status: **proposed policy**. No model, provider, inference location, hardware capability, or budget has been selected. Specific candidates must be checked against current official documentation, licensing, capabilities, privacy terms, and pricing when their milestone begins.

## Categories

- **Hosted API:** a provider runs inference. Usually easiest to start and scale, but adds data-governance, network, availability, and usage-cost concerns.
- **Open-source model/software:** source code is available under a licence that permits inspection and defined uses; model weights may or may not be included.
- **Open-weight model:** weights are available under stated terms, which may still restrict use and need not satisfy an open-source definition.
- **Small language model (SLM):** a relatively compact model that may reduce latency and cost for bounded tasks; "small" does not guarantee adequate quality or that it runs on available hardware.

Inference may run on a hosted service, the owner's laptop/local infrastructure, or on-device. These have different privacy, availability, performance, battery/memory, maintenance, and cost trade-offs. The Samsung Galaxy S23 Ultra is the initial test device, not proof that any particular model is suitable on-device. Laptop hardware is unresolved.

## Route by task, not prestige

| Task | Start with | Escalate when |
| --- | --- | --- |
| Classification/routing | Deterministic MIME/metadata rules, then a small structured classifier if needed. | Ambiguity measurably harms downstream quality. |
| Entity/relationship extraction | Schema-constrained model with evidence spans and confidence. | Complex language or conflicts fail extraction thresholds. |
| OCR/image understanding | Deterministic OCR or modality-specific model. | Layout or visual semantics require stronger multimodal reasoning. |
| Embeddings | One versioned embedding model per comparable index experiment. | Held-out retrieval shows a material gap worth re-indexing cost. |
| Reranking | No reranker in the simplest baseline, or a lightweight scorer. | Candidate recall is adequate but ranking quality is not. |
| Answer generation | Smallest option meeting groundedness, citation, and abstention criteria. | Complex synthesis improves enough to justify added latency/cost. |
| Later transcription/video | Existing transcripts or bounded transcription first. | Audio quality, speakers, or visual context require specialised processing. |

Deterministic rules are preferred when inputs and outcomes are predictable. A small model is appropriate when its measured errors are acceptable. A stronger model is justified only when evaluation shows a useful improvement for the task.

## Routing policy

A versioned router should consider task, modality, input size, sensitivity/privacy policy, measured quality, latency target, availability, and estimated cost. Its output should be structured and include the selected route, policy version, reason code, fallback, and applicable limits.

Fallbacks must be explicit. Examples include deterministic extraction after a model outage, a smaller/cheaper answer model, deferring expensive media work, preserving an original while marking extraction failed, or abstaining. Routing retries and escalation depth are bounded.

Routing decisions, timings, token/usage measures, failures, and model/configuration versions should be logged in privacy-aware traces. Personal payloads should be excluded or redacted by default. Evaluation compares per-task routes against a fixed baseline; it does not assume a single model is best for everything.

## Candidate selection process

At the relevant milestone:

1. Define the task dataset, quality metric, privacy constraints, latency target, and maximum cost/resource envelope.
2. Verify provisional candidates using current official model cards/documentation, licences, supported modalities/context, deployment requirements, and pricing.
3. Test hosted, open-weight/local, and deterministic or SLM options where feasible; document exclusions such as unavailable hardware.
4. Compare quality, failure behaviour, latency, privacy, operating burden, and total processing cost.
5. Record the decision, fallback, version, evidence, and re-evaluation trigger.

## Unresolved decisions

- Allowed data destinations and whether personal content may reach hosted inference.
- Available laptop/server hardware and acceptable on-device battery/storage use.
- Candidate models/providers, licences, deployment method, and fallback order for each task.
- Quality thresholds, latency targets, token/execution limits, and budget.
- How model artifacts and versions are retained for reproducibility.
