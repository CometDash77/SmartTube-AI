# T13 local export — Jev audit and source adjudication

Date: 2026-09-20, Asia/Hong_Kong. Branch `production`. No commit, push, install or release.

Method: one live `audit` batch of seven atomic claim/evidence checks about the new export code, designed with
the skill's Grill-the-judgment step (one claim per row, verbatim bounded source excerpts with locators, no
requirements list turned into a positive claim). Raw material is preserved:

- input: `docs/plans/evidence/subtitle-t13-jev-input.json` (13,963 bytes, 7 checks)
- result: `docs/plans/evidence/subtitle-t13-jev-result.json`

Actual call: 1 request, model `typesafe/jev-1.13-20260917`, input sha256
`e2d4f92855bdc3f643c3b8aeae157a5f16a2b1c154bdaadc063bb2e0287cb593`, 5,228 input / 320 output tokens,
1,081.44 ms, no retry and no provider fallback.

Local acceptance policy: a relation counts only at confidence >= 0.70 **and** winner probability >= 0.85
**and** margin >= 0.20. Everything else is adjudicated by hand against the source. Jev never certifies
completion; compilation, tests, lint and device checks stay separate.

| id | Jev answer (confidence) | local decision | basis |
| --- | --- | --- | --- |
| click-time-binding | supports (0.90) | accepted | thresholds met (0.94 vs 0.06) and corroborated by `SubtitleExportControllerTest.theSnapshotIsFixedBeforeTheBackgroundWorkStarts` |
| write-truthfulness | supports (0.90) | accepted | thresholds met (0.93 vs 0.06); the store compares stored length with the written length and skips existing names |
| repeated-tap-guard | supports (0.78) | accepted | 0.85 vs 0.14, margin 0.71; corroborated by `aSecondPressWhileBusyIsRefusedInsteadOfQueued` |
| diagnostics-always-available | supports (0.73) | manual: supports, scope narrowed | 0.82 < 0.85, so reviewed by hand; see below |
| report-prints-counts-only | supports (0.30) | manual: supports | low confidence, reviewed by hand; see below |
| srt-boundaries | supports (0.35) | manual: supports | low confidence, reviewed by hand against the passing boundary tests |
| no-request-no-cache-change | **insufficient** (0.66) | manual: supports after new code evidence | the supplied excerpt could not establish it; the missing part is a code check, not a semantic one |

## Manual adjudications

### diagnostics-always-available (Jev 0.82 supports — below threshold)

`SubtitleExportController.build` routes the diagnostics kind to `SubtitleDiagnosticReport.buildUtf8` **before** it
looks at any timeline, and `SubtitleDiagnosticReport.build` substitutes `Source.none()`, `Session.idle()`,
`Counters.empty()` and a coverage measure over a null timeline, so the report is produced with
`timelineFrames=0`, `snapshotStatus=NOT_REQUESTED`, `aiEnabled=false` and `keyConfigured=false`
(`SubtitleDiagnosticReportTest.reportWorksWithoutAnySessionTimelineOrKey`).

Scope narrowing: the plan's requirement is that the diagnostic export is *usable* without a subtitle timeline,
a key or AI. Whether the bytes reach the public directory still depends on the platform storage path, and on
Android below 10 the storage permission can legitimately be refused. That refusal is a distinct, explicit
outcome (`PERMISSION_DENIED` with an actionable message plus the system permission request) and is not a silent
failure, so the claim is supported inside its real scope, not unconditionally.

### report-prints-counts-only (Jev 0.53 supports, confidence 0.30)

Verified by hand from the source and by test:

- The translation map reaches the report only through
  `SubtitleSrtFormatter.measure(timeline, translations)`, whose result is used as `getItems()` /
  `getTranslatedItems()` / `getFrames()` integers; no map value is appended.
- Every printed value passes `SubtitleDiagnosticReport.value(String)`: one line, control characters dropped,
  `=` rewritten, hard cap of 80 characters. The fields printed that way are application/device/source
  metadata (`appVersionName`, `packageName`, `androidRelease`, `deviceManufacturer`, `deviceModel`,
  `targetLanguage`, `sourceType`, `sourceMime`, `sourceLanguageCode`, `sourceVssId`,
  `snapshotStatus`) and the sanitised event codes.
- The event log sanitises on write: `SubtitleExportEventLog.sanitize` keeps only `[A-Z0-9_]` up to 48
  characters, so a key, a URL or a token cannot survive inside an event entry.
- `PlaybackPresenter.buildAiExportSnapshot` deliberately copies only the safe parts of the selected source and
  never reads `SelectedSubtitleSource.getBaseUrl()` (the memory-only locator).
- `SubtitleDiagnosticReportTest.reportLeaksNoSecretAndNoSubtitleText` asserts that the real subtitle text, a
  fake key and a fake tokenised URL never appear and that no `key=value` line contains `http://`,
  `https://`, `Bearer`, `Cookie` or `Authorization`.

### srt-boundaries (Jev 0.57 supports, confidence 0.35)

Adjudicated from the source plus the executed tests rather than from Jev's verdict:
`SubtitleSrtFormatterTest` (12 cases) pins `00:00:00,000`-style timestamps including a negative clamp, the
exact CRLF cue text, that an empty frame is a clearing boundary and not a cue, that a zero-length frame is
never written, that the final unknown end borrows the longest known cue length (with a 3 s default when there is
no earlier cue), and that multi-line, CJK and emoji text round-trips. This is deterministic logic, so the tests
are authoritative and Jev was only a cross-check.

### no-request-no-cache-change (Jev insufficient)

Jev was right that the excerpt could not show this: it showed the bundle's head and the cache constants, not the
absence of a transport. The missing evidence is a code fact, so it was established with code instead of a second
request:

- `rg` over the nine export classes for `okhttp|OkHttp|HttpURLConnection|SubtitleTranslationClient|SubtitleTranslationService|SubtitleTranslationDispatcher|SubtitleRequestBuilder|SubtitleTranslationRequest|Socket|java\.net|openConnection` -> **0 matches**.
- `rg` over the same nine classes for a cache mutator (`put`/`clear`/`clearFailures`/`clearFailure`/`recordFailure`) -> only `SubtitleExportEventLog:46: mEvents.clear();`, i.e. the event ring, not the translation cache.
- `SubtitleTranslationCache.MAX_ENTRIES` (2,000) and `MAX_BYTES` (2 MiB) are unchanged by this round and no export class references them for writing.
- `SubtitleExportControllerTest.theSnapshotIsFixedBeforeTheBackgroundWorkStarts` and
  `diagnosticsNeedNoTimelineNoKeyAndNoAiSwitch` exercise the whole controller path with a fake writer, and no
  test transport exists in the export tests.

Therefore the claim is supported by code evidence. No second live request was sent: the verdict was
`insufficient` because the evidence bundle was thin, not because of a provider error, and the missing part is
deterministic and cheaper to settle in code (the skill's own guidance for exact checks).

## What Jev did not check (and what remains open)

- Jev's `not_checked`: evidence authenticity/freshness, code correctness, overall task completion. None of the
  seven rows certifies that T13 is finished.
- Not executed anywhere yet: `:common:testStbetaDebugUnitTest`, `:common:lintStbetaRelease`,
  `:smarttubetv:lintStbetaRelease`, debug/release compile and APK assembly/signature checks (project policy:
  GitHub Actions).
- Not verified on a device: remote focus on the two new menu entries, one-press export, file-manager
  visibility/copy of `Documents/SmartTube/Exports/`, permission refusal, out-of-space feedback and playback
  continuity during an export.
- The tests behind the adjudications above were compiled with JDK 17 `javac` and executed directly with
  JUnitCore (JUnit 4.12) against the checked-in ExoPlayer core jar and android.jar; the result was
  **OK (45 tests)** across the five new test classes. That is local evidence, not a substitute for the Gradle
  task in CI.
