# JustDump

JustDump is a durable personal knowledge application, initially for Android. It is designed to make capturing text, links, images, and screenshots effortless, organise saved material, and answer questions using the owner's content with inspectable source references. Documents, audio, and video processing are longer-term capabilities.

## Project status

Milestones 1, 2, and 3A are complete. The Android app has a verified device-local text vault and explicit owner-authenticated cloud upload/read-back through a laptop-local FastAPI process. Milestone 3R synthetic retrieval/evaluation work is in progress and unverified. There is no hosted API, automatic synchronisation, cloud backup guarantee, or Android question UI. Proposed technologies and unresolved decisions are not commitments.

## Reference documents

- [Product requirements](docs/product-requirements.md)
- [UX principles](docs/ux-principles.md)
- [Architecture](docs/architecture.md)
- [Model strategy](docs/model-strategy.md)
- [Evaluation strategy](docs/evaluation-strategy.md)
- [Roadmap](docs/roadmap.md)
- [Progress](docs/progress.md)
- [Learning notes](docs/learning-notes.md)
- [Contributor and agent working agreement](AGENTS.md)

The repository may later add `backend/`, `evals/`, `contracts/`, `infra/`, and `scripts/` as milestones require them. They should not be created early merely to fill out a planned layout.
