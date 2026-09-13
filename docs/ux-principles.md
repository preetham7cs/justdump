# UX principles

Status: **proposed**. These principles translate product goals into a direction for later design; no UI has been implemented or tested.

## Capture first

The home screen should feel like an inviting place to put something, not an operations dashboard. Typing, pasting, choosing a file/image, and asking a question should be obvious. Android **Share -> JustDump** should minimise steps and confirm when the content is durably saved.

Technical details such as model names, embeddings, graph execution, job identifiers, and retry counts belong in diagnostics, not normal flows.

## Proposed information architecture

A compact bottom dock is one candidate:

- **Recents:** the chronological home for newly captured and recently used items, including quiet status indicators.
- **My World:** exploration of people, places, topics, activities, and relationships found in saved knowledge. Useful lists, clusters, timelines, and filters should be considered before assuming a node-link graph.
- **Favourites:** intentionally pinned material for quick return.

The labels, number of destinations, default destination, and treatment of the ask entry point remain unresolved and require design exploration. Asking could be a persistent action, part of the capture surface, or a dedicated destination.

## Feedback and state

| State | What the owner needs to understand | UX direction |
| --- | --- | --- |
| Empty | What can be saved and how to begin. | One clear capture invitation with a few examples. |
| Offline | The item is safe locally but not yet uploaded. | Reassuring confirmation and a quiet offline marker. |
| Uploading | Transfer is underway without blocking another capture. | Lightweight progress; preserve navigation and capture. |
| Processing | The original is saved, but searchable representations may be incomplete. | Plain-language status without AI pipeline terminology. |
| Failed | What remains safe, what failed, and what the owner can do. | Actionable message, retry where safe, and access to the original. |
| Ready | The item is available for browse/search at its current processing version. | Minimal confirmation; do not demand attention. |

Partial success matters. For example, a URL may be saved while page extraction fails, or an image may be stored while OCR fails. The interface must not collapse these into a misleading single success state.

## Trust and evidence

- Preserve and expose originals; derived summaries or entities must never masquerade as the source.
- Answer references should open the supporting item and, where possible, the relevant passage, image region, or later media timestamp.
- Conflicting or insufficient evidence should be visible in the answer experience.
- Destructive actions should clearly explain whether derived data and relationships are also removed.

## Design checks for later milestones

Prototype and test capture prominence, one-handed use, accessibility, state comprehension, source inspection, error recovery, and question entry on the physical test device. Visual style and navigation are not confirmed until demonstrated and reviewed.
