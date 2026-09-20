# AI subtitle implementation — acceptance status against plan section 11

> **Historical matrix — superseded current status:** Later CI results and the latest user-reported failure are indexed in [current status](../subtitle-current-status.md). This matrix retains earlier evidence; statements about missing delivery, failing baseline tests and lint coverage below are historical, not current blockers. Production acceptance has not passed.

Date: 2026-09-20, Asia/Hong_Kong. Scope: what is actually verified today, from which command or test,
and what is explicitly **not** verified. This is an acceptance matrix, not a progress log; the single
daily progress record stays in `docs/development/2026-09-20.md`.

## Historical automated evidence (reported by earlier implementation rounds)

Retrospective correction: these results were not rerun in this review. New Gradle checks and APK delivery must run on GitHub per plan section 15. The full module has two recorded failures and lint registry coverage is incomplete; do not label all gates passed. The T13 local export is now implemented locally with tests (see the T13 section below), but no Gradle check, lint, device check or GitHub prerelease run has been performed for it either.

| Command | Result |
| --- | --- |
| `./gradlew.bat :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.exoplayer.other.*"` | BUILD SUCCESSFUL; the whole AI subtitle suite passes (per-class counts in the daily record) |
| `./gradlew.bat :common:testStbetaDebugUnitTest` (complete module task) | 353 tests, 2 failed (latest recorded full run: daily log round 126; older run: 351) — both are the **pre-existing** `ScreensaverManagerTest` cases unrelated to subtitles (they fail identically at session start under JDK 11; under JDK 17 that class fails wholesale on Robolectric class-file handling) |
| `./gradlew.bat :smarttubetv:compileStbetaDebugJavaWithJavac` and `:common:compileStbetaReleaseJavaWithJavac` | BUILD SUCCESSFUL (debug and release variants) |
| `./gradlew.bat :smarttubetv:assembleStbetaDebug` | BUILD SUCCESSFUL; four stbeta debug APKs (arm64-v8a, armeabi-v7a, universal, x86) |
| `./gradlew.bat :smarttubetv:assembleStbetaRelease` | BUILD SUCCESSFUL (1 m 6 s); four release APKs. **Signing not verified** (no signature check was performed, so no signing claim is made) |
| `./gradlew.bat :common:lintStbetaRelease` | **BUILD SUCCESSFUL with 0 errors**: the gate exposed and forced the fix of five real minSdk violations (GCMParameterSpec, StandardCharsets, AndroidKeyStoreAccess, BooleanSupplier, Comparator.comparingLong), taking lint from 15 errors to none |
| `./gradlew.bat :smarttubetv:lintStbetaRelease` | **BUILD SUCCESSFUL**. The log still prints `UnsupportedClassVersionError: androidx/work/lint/WorkManagerIssueRegistry` because lint's WorkManager registry is built for Java 17 while Gradle here runs on JDK 11 (tests need JDK 11: Robolectric 4.6.1 cannot run under 17); it does not fail the task |
| `git diff --check` (build files) / `git submodule status` | exit 0 / SharedModules unchanged at 13f5687d; MediaServiceCore keeps the pre-existing upstream-recorded gitlink drift, checkout untouched |
| Jev (live, `audit`) | 5 calls: T01/T02 (8 checks, 1 above thresholds, 7 adjudicated), T03 (5 checks, 3 above, 1 adjudicated, 1 evidence gap), T04 (5 checks, 4 above, 1 adjudicated), T05 (4 checks, 3 above, 1 adjudicated), T06/T10 (5 checks, **all above**) |

## Section 11 matrix

| Scenario | Automated proof | Device/real-service complement | Status |
| --- | --- | --- | --- |
| Correct source | `SubtitleSourceBinderTest`, `SubtitleManifestAdapterTest`, `SubtitleEndToEndTest`; Jev T06 batch supports the binding claims | switch between two tracks with distinguishable text | automated **verified**; device **not verified** |
| Correct timing | `SubtitleTimelineBuilderTest`, `SubtitleSnapshotReaderTest` (boundaries compared with an independent native decode) | non-zero start position, repeated seeks, speed change | automated **verified**; device **not verified** |
| Three modes | `SubtitleComposerTest`, `SubtitleManagerTest`, `SubtitleFrameTranslationsTest`; Jev T04 batch | instant switching, font/position/multi-line/RTL | automated **verified** (text level); layout/RTL **not verified** |
| Prefetch efficiency | `SubtitleBatchPlannerTest`, `SubtitleTranslationCacheTest`, `SubtitleTranslationDispatcherTest`, `SubtitleEndToEndTest` | network latency injection, real hit rates | automated **verified**; real-service measurement **not done** |
| Lifecycle | `AiSubtitleControllerTest`, `AiSubtitleSessionBinderTest`, `SubtitlePrefetchLoopTest`, `SubtitlePrefetchTickerTest`; Jev T05/T06 batches | D-pad bursts, return to player, consecutive videos | automated **verified**; device **not verified** (residual-timer behaviour included) |
| API correctness | `SubtitleRequestBuilderTest`, `SubtitleResponseParserTest`, `SubtitleResponseHandlerTest`, `SubtitleRetryPolicyTest`, `SubtitleEndpointTest`, `SubtitleCredentialsTest`, `SubtitleOkHttpWireTest` (local synthetic service) | authorised real-key minimal test | automated **verified** (local HTTP only); real DeepSeek call **not done** |
| Key and configuration | `SubtitleKeyEnvelopeTest`, `SubtitleKeyCipherTest`, `PersistentSubtitleKeyStoreTest`, `SubtitleKeyFileStorageTest`, `SubtitleKeyBackupRulesTest`, `SubtitleKeyStoreFactoryTest`, `MemorySubtitleKeyStoreTest`, `SubtitleAiPrefsStoreTest`, `AppPrefsSubtitleAiBackendTest`, `SubtitleAiSettingsControllerTest` | Keystore restart/invalidation, backup/restore, clear key | automated **verified** (JVM crypto, storage paths, backup rules); real keystore and export **not verified** |
| Original behaviour preserved | `OriginalSubtitleNormalizerTest` (including a differential test against the former inline implementation), `AiSubtitleControllerTest` (AI off) | CC memory, auto-translated tracks, styles, channel preferences, SABR | automated **verified**; device **not verified** |
| Local one-click export (T13) | GitHub run 35483822239: required test scope (`--tests "...exoplayer.other.*"`) BUILD SUCCESSFUL on JDK 11; `:common:lintStbetaRelease :smarttubetv:lintStbetaRelease` BUILD SUCCESSFUL on JDK 17; `:smarttubetv:assembleStbetaDebug` BUILD SUCCESSFUL; `apksigner verify` "Verifies" for all four APKs; prerelease published. Locally: 5 new test classes also run with JUnitCore (OK, 45 tests) and 154 with neighbours | remote focus, one-press export, file-manager visibility/copy of `Documents/SmartTube/Exports/`, permission refusal, out-of-space feedback, playback continuity | automated **verified** on GitHub (test scope, lint, assemble, signature) + local javac/JUnitCore; device **verified** for the essential T13 path on 2026-09-20 (`32.53-nightly-9`, TCL/Android 11): original-only export with AI off and no key on an **ASR** track produced the documented ZIP (checked locally against the returned artefacts in `device-logs/2026-09-20/`); translated/bilingual/failed states and the permission/space/focus/playback checks remain **not device-verified**; project-signed release path **not available** (no signing secrets) |

## Explicit gaps (do not read as completed)

1. **No device or emulator**: remote focus, real multi-line/RTL layout, engine rebuild/rotation and
   residual-timer behaviour have no device evidence.
2. **No real DeepSeek call** and **no real Android keystore**: the encrypted store, the no-backup path
   and the backup-export check are verified only at the format/logic level.
3. **Source MIME risk (T03)**: Jev judged the available evidence insufficient for "the MIME declared by
   a real YouTube source matches the payload the timedtext endpoint returns". T10 must capture one real
   source before this is called supported. The reader attempts supported formats and handles runtime failures; missing acceptance evidence is not itself a runtime refusal switch.
4. **Release/signing**: debug and release variants both compile and assemble, but **no signature check**
   was performed and release lint did not run with its full issue registry under JDK 11, so release quality
   gates are not claimed; no installation or publication happened.
5. **No commit, push or publication** was performed by this session.

## GitHub verification and the first published prerelease (2026-09-20)

Workflow: `.github/workflows/CI.yml` (renamed, `production` push + manual dispatch, two jobs). Commits on
`origin/production`: `dc481477` (T13 source, tests, docs, 28 files), `a0a1ae60` (CI + `.gitignore`),
`af4eddca` (notes fence-quoting fix).

Verified by GitHub run 35483822239 (commit `a0a1ae60`, conclusion **success**):

| Check | Result |
| --- | --- |
| Unit tests, required scope (JDK 11) | `./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.exoplayer.other.*"` -> BUILD SUCCESSFUL in 2m17s |
| Full common module suite (informational) | `398 tests completed, 2 failed`; both failures are the pre-existing `ScreensaverManagerTest` cases. 398 = the previously recorded 353 + the 45 tests added by T13 |
| Compatibility lint (JDK 17) | `:common:lintStbetaRelease :smarttubetv:lintStbetaRelease` -> BUILD SUCCESSFUL in 2m31s (includes the `java.util.Objects` API-19 fix) |
| APK assembly | `:smarttubetv:assembleStbetaDebug` -> BUILD SUCCESSFUL in 1m48s, four ABIs |
| Signature verification | `apksigner verify --verbose --print-certs` -> "Verifies" for all four APKs; V2 signer certificate SHA-256 `9e80731d74ee74b46e2c8a0ee7b7e32b7271ee8031f313db7087ffd4c49e73ab` |
| Prerelease | tag `stbeta-32.53-nightly-2-2-debug`, target `a0a1ae60`, four APKs + `SHA256SUMS.txt`; universal SHA-256 `5f63c4145e9119707019271486f2a7ccc4e09c19a5d6bb1b373a6bf6e5b1005e` |

Signing identity: the repository has **no secrets at all** (`actions/secrets` -> `total_count=0`), so the
workflow refused to ship an unsigned release APK and published a clearly labelled **debug-signed fallback**
instead (the AGP debug keystore of that runner; V2-signed, so it installs on current Android). A project-signed
release build needs `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS` and `KEY_PASSWORD`. The same signing facts
mean an installed `org.smarttube.beta` signed with another certificate cannot be updated in place, and a later
fallback run uses a different debug key.

Also observed, so a later session does not read it as a workflow defect: on this fork an ordinary push to
`production` produced no run at all; `gh workflow run CI.yml --ref production` is what starts one.

## T13 local export — implemented locally, not device-verified

Added 2026-09-20 (DSH round 52). Source: nine new classes under
`common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/` plus the
`SubtitleTranslationCache`, `AiSubtitleSessionBinder`, `PlaybackPresenter` and `PlayerUIController`
integrations and 15 strings in each of `values`, `values-zh` and `values-zh-rTW`. Details and the exact
per-claim adjudication are in `subtitle-t13-review.md` and plan section 14.1.

What is evidenced locally:

- JDK 17 `javac` compiled the seven pure classes, the file store, the environment reader, the cache and the
  binder against the real `android.jar` (SDK 34), the checked-in ExoPlayer core jar and the previously
  compiled `common` classes: exit 0. `PlaybackPresenter` and `PlayerUIController` compile with the same
  approach; their only 15 diagnostics were the not-yet-regenerated `R.string.ai_subtitle_export_*` fields, and
  all 15 keys were confirmed present in the three locale files.
- The five new test classes compiled and ran with JUnitCore (JUnit 4.12, JDK 17): **OK (45 tests)**. Four
  first-run failures were defects in the test code itself and were fixed.
- One live Jev `audit` (7 claims, `typesafe/jev-1.13-20260917`, 1 request, no retry/fallback): three rows above
  the local thresholds, four adjudicated by hand against the source. Input, result and the manual decisions are
  preserved under `docs/plans/evidence/subtitle-t13-*.json` and `subtitle-t13-review.md`.
- Deterministic checks: the nine export classes contain zero transport/network references; the only cache-looking
  mutator is the event ring's own `clear()`; the cache limits (2,000 entries / 2 MiB) are untouched.

Explicitly **not** verified:

- No Gradle task was run: `:common:testStbetaDebugUnitTest`, both `lintStbetaRelease` tasks, debug/release
  compile and APK assembly/signature checks stay GitHub-only per plan section 15.
- No device or emulator was available, so the two menu entries' remote focus, one-press export, file-manager
  visibility and copy of `Documents/SmartTube/Exports/`, storage-permission refusal, out-of-space feedback and
  playback continuity during an export have no device evidence.
- The public-directory choice is implemented and reasoned from the existing `MediaStoreFile`/backup path, but
  "the user can see and copy the file with a TV file manager" is a device claim and is not made here.

## How the feature behaves in the player (usage notes)

1. **Enabling.** Long-press the closed-caption button to open the existing subtitle dialog; the AI area
   at the bottom carries the per-video switch, the target language, the display mode, a status line and
   the key entries. Nothing translates until the switch is turned on, and the switch is per video.
2. **Key.** "AI translation settings" opens the existing masked input. An empty submission is refused;
   a saved key is used for the already selected track immediately. From API 23 the key is encrypted
   with a device keystore into the no-backup directory; below that it lives in memory for the session
   only and the UI says so. "Clear key" is a separate entry and clears both the store and the session.
3. **Translating.** While AI is on, the player is polled once a second for the position; the selected
   source's timeline is fetched at most once per source and translated in batches of at most 20 items /
   6,000 code points, always keeping the original subtitles on screen. The current frame is repainted
   from the cache as soon as a translation exists.
4. **Display modes.** "Original only", "Translation only" (falls back to the original while waiting or
   on failure) and "Bilingual" (original above, translation below; a missing translation shows the
   original alone). Switching modes only repaints: it never fetches and never reloads the video.
5. **Degradation.** A failed batch keeps the original; repeated failures pause translation for 30 s
   (the status line says so); a rate limit is honoured through `Retry-After`; authentication or billing
   errors stop the session and ask for a configuration fix; damaged or truncated answers are refused as
   a whole rather than half-applied.
6. **Lifecycle.** Seek, engine release and a subtitle-source change abandon any in-flight translation
   (its late answer is neither cached nor shown); turning AI off restores the original immediately;
   closing subtitles ends the session but keeps the per-video AI intent for when they are shown again.

---

## N1–N9 session update (2026-09-20, later session)

This section records what the N1/N2/N5/N7 work of the same day actually verified. Everything above stays
as the historical record of the earlier stage.

### New automated evidence

| Area | Proof | Status |
| --- | --- | --- |
| Request identity and same-source dedup (N1) | `SubtitleTimelineSchedulerTest` (8) + `SubtitleTimelineCoordinatorTest` (9), run inside the required CI scope on JDK 11: the same source fetches once; a superseded attempt's late success **and** late failure install nothing, overwrite no status and free no newer slot; release and subtitles-off abandon the attempt; an explicit later event retries after a failure; the same payload in a new manifest generation is re-attributed instead of re-downloaded; the production fetch path (`systemFetcher`) installs a decoded timeline | automated **verified** |
| Key chain and auth stop (N2) | `SubtitleAiSettingsControllerTest` (+5: save through the active store, blank refused, origin change forgets the key, same origin keeps it, clear notifies both listeners), `SubtitleTranslationServiceTest` (+2: 401/403/402 stops until a success, an unrelated failure does not), `SubtitleAiMenuStateTest` (+2: the AUTH_FAILED state) | automated **verified** |
| Settings menu and connection test (N5) | `SubtitleConnectionTestTest` (9: OK, not configured, an unusable address refuses before any call, per-status classification, transport failure, damaged answer, the synthetic batch carries no watched subtitle text, the attempt is cancellable) | automated **verified** (class logic); real service **not verified** |
| Baseline quality (N7) | The two pre-existing `ScreensaverManagerTest` failures were traced to a stale expectation: the test asserted release after `onPause`, while `MotherActivity` deliberately suspends on `onStop` (`a7d6d06a`, to avoid dim flicker). The cases now drive the real `pause+stop` sequence and the misleading comment was corrected; production behaviour unchanged. The CI lint log (JDK 17) contains **no** `WorkManagerIssueRegistry` error, so remote lint covers the full registry — the local JDK 11 caveat above does not apply to CI | automated **verified** |
| Version and upgrade strategy (N4) | `versionCode` 2444 is reserved for the next stable release; CI derives every candidate as `versionCode - 1` (2443), so a candidate installs over anything released, the stable upgrades over the candidate, and nightly candidates never consume future stable numbers | policy **implemented**; real upgrade evidence **not verified** |
| Stable-signed RC path (N8) | `.github/workflows/CI.yml` gained `workflow_dispatch.release_mode`: with the four secrets it assembles `stbetaRelease` at the reserved versionCode with the plain versionName and publishes a unique prerelease; without them it fails before building and publishes nothing (no debug fallback) | path **implemented**; **blocked**: the repository has no secrets, so no project-signed RC exists yet |

### Round-2 device acceptance: **failed** (2026-09-20 12:47–12:49, nightly-18 / TCL Android 11)

Four diagnostic reports (committed verbatim in `device-logs/2026-09-20/`) show `sourceBound=false`,
`snapshotStatus=NOT_REQUESTED`, **zero** `TIMELINE_REQUESTED` events (the 40-entry ring was never full) and
29 rejected subtitle exports, while two diagnostic exports succeeded on the same session. Conclusion: at export
time no *bound* subtitle source existed, so nothing could be exported; the AI switch is irrelevant (identical
failures before and after `AI_ENABLED`). The user-facing defects are that the app never says "select a text
subtitle track first", that the API-key entry is labelled "AI translation settings" (no "Key" anywhere, no
saved-state display, no save confirmation) and that the status line shows "Translating" while AI is off.
The diagnostics cannot yet distinguish "no track selected" from "track present but source unbound" — that gap
(plus the per-run debug signing that forces a data-wiping reinstall) is the reason this round needed a human
round trip. Details, evidence and the reviewable fix list: `subtitle-ai-tv-acceptance-round2-debug-2026-09-20.md`
and plan section 20.

### What this session did **not** verify

1. No project-signed RC: `gh secret list` is empty (SIGNING_KEY / KEY_STORE_PASSWORD / ALIAS / KEY_PASSWORD missing).
2. No device check of the translated/bilingual/partial/failed exports, permission refusal, out-of-space,
   remote focus or playback continuity during an export.
3. No real DeepSeek call, no real Android Keystore / backup-export check and no API 17–22 device evidence.
4. The new settings entries and the connection test were not exercised on a TV.
