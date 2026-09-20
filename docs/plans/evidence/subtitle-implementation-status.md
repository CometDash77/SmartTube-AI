# AI subtitle implementation — acceptance status against plan section 11

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
| Local one-click export (T13) | 5 new test classes executed with JUnitCore: SRT boundaries/Unicode/partial translations, ZIP entries and coverage note, diagnostic whitelist and leak checks, click-time snapshot and one-job guard (OK, 45 tests) | remote focus, one-press export, file-manager visibility/copy of `Documents/SmartTube/Exports/`, permission refusal, out-of-space feedback, playback continuity | local **verified** (javac + JUnitCore, not Gradle); Gradle tests/lint **not run**; device **not verified** |

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
