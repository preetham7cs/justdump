# Learning notes

Use one copy of this template per meaningful concept or experiment. Keep observations separate from conclusions, and link to code, traces, or evaluation output when those artifacts exist.

## Entry: Android build-to-device foundation

- **Date:** 2026-09-13
- **Milestone:** 1, Android app on the phone
- **Question or concept:** How does a minimal Compose source tree become an installed, launchable Android application?
- **Why it matters to JustDump:** Every later feature depends on a reproducible build, stable application identity, and a trustworthy physical-device feedback loop.
- **Decision status:** Confirmed for Milestone 1

### Mental model

Gradle is the build orchestrator and dependency resolver; the checked-in Gradle wrapper pins its version for every developer and CI machine. The Android Gradle Plugin teaches Gradle how to process Android resources and the manifest, compile code, package an APK, and create build variants. Kotlin provides the application language, while Jetpack Compose turns composable functions into the native UI. `AndroidManifest.xml` connects compiled code to Android by declaring the application and its launchable activity.

The application ID is Android's durable installation identity; the display name is a replaceable user-facing resource. `compileSdk` controls which platform APIs can be compiled against, `targetSdk` declares the behaviour level the app has tested for, and `minSdk` controls the oldest OS allowed to install it. Debug signing is automatic local development identity, not a production key-custody solution.

### Options and hypothesis

- Options considered: latest Compose libraries requiring API 37, or a stable Compose release compatible with the approved compile SDK 36.
- Trade-offs: the newest Compose release would require changing an approved SDK boundary; Compose BOM 2026.04.01 provides stable Compose 1.11 without doing so.
- Expected result and reason: AGP 9.4.0, Gradle 9.6.0, built-in Kotlin with Compose compiler 2.3.21, and API 36 should build under Android Studio's JDK 25 because the official compatibility requirements are met.

### Experiment

- Setup and fixed inputs: Android Studio Quail 4; bundled OpenJDK 25.0.3; SDK Platform 36; Build Tools 36.0.0; ADB 37.0.1; Samsung SM-S918U1 over authorised wireless ADB.
- Steps or command: `gradlew.bat clean lintDebug assembleDebug`, verify the APK with `apksigner`, install with `adb install -r`, cold-launch with `adb shell am start -W`, then inspect focus, UI hierarchy, and the crash buffer.
- Metric or observable result: build and lint exit successfully; install returns `Success`; launch returns `Status: ok`; expected text is present in the focused activity; no matching fatal crash appears.
- Conditions that would falsify the hypothesis: dependency or compiler incompatibility, failed lint/build/signature/install, wrong focused package, missing welcome text, or a startup crash.

### Results

The initial packaging command was interrupted by the terminal timeout and left a partial generated APK. A retry correctly exposed the duplicate-entry packaging error. Gradle `clean` removed generated output, after which all 47 build/lint tasks completed successfully. Final lint reported zero errors and six intentional version-availability warnings from retaining the approved API 36/toolchain pins. The APK uses the standard `Android Debug` certificate and installed successfully. The final cold launch completed in 746 ms wait time; Android reported `MainActivity` focused, and an earlier inspection exposed `JustDump` in the live UI hierarchy. The owner visually confirmed the welcome screen and successfully reopened the app from its launcher icon.

### Conclusion

- What was learned: first builds are dominated by dependency/tool downloads; interrupted generated artifacts can require a clean rebuild, while source remains unaffected.
- Decision: retain the approved API 36 boundary with Compose BOM 2026.04.01 and the verified Gradle/AGP/Kotlin versions.
- Limitations or uncertainty: this milestone verifies only the minimal debug app on the physical test phone; it does not verify any capture, persistence, backend, authentication, or AI behaviour.
- Follow-up question: none for Milestone 1; discuss and agree Milestone 2 separately.

## Entry: Device-local notes and coordinated drafts

- **Date:** 2026-09-13
- **Milestone:** 2, Capture-first local text vault
- **Question or concept:** How can a Compose editor feel immediate while text notes and a single draft remain durable without a stale background write reviving saved content?
- **Why it matters to JustDump:** Capture must work without a network and must make its device-local durability boundaries understandable before cloud synchronisation exists.
- **Decision status:** Confirmed

### Mental model

Compose observes a `ViewModel` state flow and renders it; it does not own the durable note. The ViewModel forwards note and draft operations to a repository, and Room is the durable device-local source of truth. A `SavedStateHandle` is deliberately limited to the small selected-note ID, not note bodies.

Each edit receives a monotonically increasing draft generation. A 500 ms debounce writes only the generation it observed. Saving captures both the text and generation in one Room transaction, then clears the draft only up to that generation. Older delayed writes are rejected, while text typed during a save has a newer generation and remains the next draft.

### Options and hypothesis

- Options considered: keep draft text only in Compose/saved state; persist on every keystroke; or use a debounced Room draft with generation coordination.
- Trade-offs: in-memory state is fast but unsafe on process death; every-keystroke database writes are simple but noisy; a debounce reduces writes but leaves a short durability window and needs race protection.
- Expected result and reason: Room plus a 500 ms generation-aware debounce and transactional save should retain intentional input without letting an older job overwrite a later state.

### Experiment

- Setup and fixed inputs: JustDump debug build, Room 3 database version 1, authorised Samsung SM-S918U1 on Android 16/API 36.
- Steps or command: run `:app:connectedDebugAndroidTest`, `:app:lintDebug`, and `:app:assembleDebug`; install with `adb install -r`; launch `MainActivity`.
- Metric or observable result: repository and ViewModel cases for stale drafts, duplicate/failed saves, repeated Save taps, and typing during save pass; lint/build/install/launch succeed.
- Conditions that would falsify the hypothesis: a stale draft reappears after save, failed save discards input, repeated taps create multiple saves, newer text disappears during save, or device tests/build/install fail.

### Results

Instrumented tests passed on the physical Samsung for the five coordination/persistence cases. `lintDebug` and `assembleDebug` passed. The debug APK installed with non-destructive `-r` and launched successfully. The owner then confirmed save/recents/detail, offline capture/read, draft restoration after close/reopen, and saved-note retention across the update.

### Conclusion

- What was learned: durable UI state needs an explicit source of truth and an ordering rule when delayed writes and user actions can overlap.
- Decision: retain Room as the local note/draft store, a 500 ms documented debounce, generation-based stale-write rejection, and transactional save/draft acknowledgement. Keep data bodies out of `SavedStateHandle`.
- Limitations or uncertainty: no system can promise the last keystroke survives an immediate force-stop before the debounce. `allowBackup=false` and database exclusions set a privacy policy, but backup/restore needs later explicit resilience-milestone verification.
- Follow-up question: discuss and agree the next narrowly scoped milestone before implementing more features.

## Entry: Explicit owner-scoped cloud text sync

- **Date:** 2026-09-16
- **Milestone:** 3A, authenticated explicit cloud upload/read-back
- **Question or concept:** How can a local-first Android note be explicitly copied to cloud storage without exposing privileged credentials or letting retries create duplicate records?
- **Why it matters to JustDump:** Cloud acknowledgement must add durable remote storage without weakening device-local capture or overstating backup/recovery guarantees.
- **Decision status:** Confirmed for the local development experiment

### Mental model

Room remains the Android UI's source of truth. A local note has a stable UUID before any network action. The app signs the owner in through Supabase Auth, obtains a user session, and sends that user bearer token to FastAPI. FastAPI verifies the JWT from Supabase's public JWKS, checks the configured single-owner UUID, validates the request with Pydantic, then calls Supabase PostgREST with the same bearer token and only the publishable key.

That last detail matters: the backend is an authentication and contract boundary, not a privileged database shortcut. Postgres RLS sees the owner JWT; the SQL RPC is `security invoker`; and the database function makes `(owner_id, client_note_id)` unique. An identical retry returns the existing immutable record. A different body for that local ID is a conflict rather than a silent overwrite.

### Options and hypothesis

- Options considered: direct Android-to-database access; a backend using a service-role key; or FastAPI forwarding the caller's JWT to PostgREST.
- Trade-offs: direct access reduces code but weakens a server-side contract boundary; service-role access would bypass RLS and increases credential risk; caller-token forwarding adds a hop but preserves RLS and owner scoping.
- Expected result and reason: Room status transitions from pending to uploading to synced only after a separate cloud read-back matches the local ID and content. A failed request must leave the local note readable and retryable.

### Experiment

- Setup and fixed inputs: Supabase Free project with one auto-confirmed owner and public sign-ups disabled; local FastAPI/Pydantic runtime; Android debug build on Samsung SM-S918U1 Android 16/API 36; ADB reverse to the laptop loopback API.
- Steps or command: run backend contract tests, Android lint/build and instrumented persistence tests; sign in on the phone; explicitly upload one note; retry the same upload; inspect the resulting cloud table row; close/reopen the app.
- Metric or observable result: unauthenticated API access returns 401; a signed-in owner receives cloud read-back confirmation; repeated upload leaves one cloud row; the local note remains readable across failure/retry and reopen.
- Conditions that would falsify the hypothesis: missing/privileged credential in the APK, a non-owner reaching cloud data, duplicate rows from repeat upload, a failed request deleting local content, or a sync label without successful read-back.

### Results

Backend tests passed 6/6, including missing-token rejection, non-owner rejection, repeat upload/read-back, and immutable-ID conflict. Android lint/build and focused persistence instrumentation passed. A real owner sign-in, explicit phone upload, and authenticated cloud read-back succeeded. Repeating verification retained one cloud row. Safe setup failures occurred during development, but each left the local note readable and retryable; the final retry succeeded. The owner closed/reopened the app and confirmed the local note and verified state remained.

### Conclusion

- What was learned: authentication, ownership, idempotency, and confirmation are separate concerns. A visible “synced” state should represent an acknowledged and read-back cloud record, not merely a dispatched HTTP request.
- Decision: retain Room as the local UI source of truth, explicit upload only, Supabase Auth email/password, owner UUID check in FastAPI, caller-token PostgREST access under RLS, and stable local UUIDs for idempotency.
- Limitations or uncertainty: the API runs only on the laptop for this experiment; no hosted deployment, automatic retry, background sync, backup/restore promise, deletion workflow, or AI processing exists. The Supabase project is not a substitute for a tested recovery plan.
- Follow-up question: separately choose between Milestone 3B automatic sync and Milestone 3R text-only retrieval/evaluation; neither is authorised by this result.

## Entry: Synthetic retrieval and grounded-answer baseline

- **Date:** 2026-09-16
- **Milestone:** 3R, text-only RAG baseline
- **Question or concept:** Does vector retrieval improve a small fixed text corpus over transparent full-text search, while answers remain tied to source-note IDs?
- **Why it matters to JustDump:** Retrieval must earn added cost and complexity before an Android question UI, LangGraph, or personal-vault model processing is considered.
- **Decision status:** Verified experiment; under owner review

### Mental model

Full-text search ranks overlapping terms. Embeddings represent passages and questions as vectors, so cosine similarity can retrieve paraphrases even when exact words differ. Neither retrieval method proves an answer true: the answer model receives only retrieved evidence and must cite original note IDs. A citation ID is structurally valid only if it came from that retrieval result; human review still decides whether the cited text supports the claim.

### Experiment

- **Setup and fixed inputs:** six synthetic cloud notes; five development and three held-out questions; owner JWT/RLS; explicit indexed fixture IDs only; `token-500-overlap-75-v1`; top three deduplicated note IDs.
- **Models:** `text-embedding-3-small` for vector mode and `gpt-5-mini` with the same strict structured-answer prompt in both modes.
- **Measures:** note-level Recall@3, MRR, deterministic citation-ID validity, abstention/conflict expectation flags, and manual answer-support/completeness review.
- **Privacy and cost:** no personal notes were selected automatically. Redacted reports omit text, questions, answers, tokens, and credentials. A durable local ledger reserved before calls; actual reconciled provider usage was $0.013036, under the $1 cap.

### Results

Full-text reached development Recall@3/MRR of 0.70/0.60 and held-out 0.67/0.33. Vector retrieval reached 1.00/0.80 and 1.00/0.67 respectively. Every reported citation ID was within retrieved source IDs. Vector retrieval found both expected conflict notes, but the answer path abstained after a generated response could not be validated; it did not invent a resolution or emit an uncited answer.

### Conclusion

- **What was learned:** retrieval and answer grounding are separate gates. Better candidates improve evidence recall but do not guarantee conflict handling or complete answers.
- **Decision:** retain full-text as a transparent baseline and retain vector retrieval as a measured comparison. After the baseline, add a narrow ISO-date conflict guard: require both conflict citations and use a deterministic conflict summary if structured generation is malformed. A direct synthetic contract test passed after increasing the bounded answer allowance from 350 to 800 tokens.
- **Limitations or uncertainty:** the corpus is too small and synthetic to establish general quality. Raw answer support/completeness needs human review. No conclusion applies to personal notes.
- **Follow-up question:** does a minimal typed LangGraph retrieve -> answer/abstain wrapper with privacy-aware tracing improve the same held-out cases enough to justify its added complexity?

## Entry: _title_

- **Date:** YYYY-MM-DD
- **Milestone:** Number and name
- **Question or concept:** What are we trying to understand?
- **Why it matters to JustDump:** What product or engineering decision does it affect?
- **Decision status:** Confirmed, proposed, or unresolved

### Mental model

Explain the concept in plain language. Define important terms and distinguish it from nearby concepts.

### Options and hypothesis

- Options considered:
- Trade-offs:
- Expected result and reason:

### Experiment

- Setup and fixed inputs:
- Dataset/split and version:
- Code, prompt, model, embedding, index, graph, and routing-policy versions (as applicable):
- Steps or command:
- Metric or observable result:
- Latency, resource/token use, and cost (as applicable):
- Conditions that would falsify the hypothesis:

### Results

Record what actually happened, including failures and links to evidence. Do not replace observations with an interpretation.

### Conclusion

- What was learned:
- Decision, if any:
- Limitations or uncertainty:
- Follow-up question:
