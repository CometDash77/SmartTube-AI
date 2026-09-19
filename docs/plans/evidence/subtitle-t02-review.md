# T01/T02 Jev audit and manual adjudication

Date: 2026-09-19, Asia/Hong_Kong. Scope: the exact-source binding that T02 must implement and the
original-cue baseline refactored in T01.

## Actual call

```powershell
python -B .agents/skills/smarttube-jev-triage/scripts/jev.py audit tmp/jev-subtitle-t02-input.json --live
```

- Mode `audit`, status `judged`, exactly 1 call, no retry, no fallback.
- Model `typesafe/jev-1.13-20260917`; elapsed 1138.72 ms; usage 4,790 input / 358 output tokens; cost 0.00020118.
- `input_sha256` `d378662cab3d1a48ecdf50424fb203d3b47fb9b65f7f21bf552ae8ac7b6f846e`.
- Input: [subtitle-t02-jev-input.json](subtitle-t02-jev-input.json) (8 checks, 10,720 bytes).
- Raw result: [subtitle-t02-jev-result.json](subtitle-t02-jev-result.json).
- `not_checked`: evidence authenticity/freshness, code correctness, overall task completion.

Local thresholds are confidence >= 0.70, winner >= 0.85, margin >= 0.20. **1 of 8 rows passed them**;
7 rows were resolved manually against the source, as required. No row authorizes completion, and no
result was reused as evidence for code that changed afterwards.

## Per-row outcome

| id | Jev answer | Highest probability / confidence | Above thresholds | Manual resolution |
| --- | --- | --- | --- | --- |
| `format-identity` | supports | 0.79 / 0.69 | no (confidence 0.69) | **supports** — see below |
| `none-state` | insufficient | 0.37 / 0.06 | no | **supports**, with one counterexample check |
| `onTracksChanged-gap` | supports | 0.53 / 0.29 | no | **supports** |
| `url-uniqueness` | supports | 0.75 / 0.62 | no | **supports**, one required condition added |
| `id-only-insufficient` | supports | 0.83 / 0.74 | no (winner 0.83) | **supports** |
| `normalizer-equivalence` | insufficient | 0.66 / 0.48 | no | **supports** — replaced by a differential test |
| `no-buffer-reset` | supports | 0.75 / 0.63 | no | **supports** |
| `pr-second-writer` | supports | 0.97 / 0.95 (margin 0.94) | **yes** | accepted as stated |

## Manual adjudication against the source

- **format-identity.** `TrackGroup(Format... formats)` assigns `this.formats = formats` without copying
  (`TrackGroup.java:50-54`), and `indexOf` documents that formats are located by identity
  (`TrackGroup.java:74-88`). `DashMediaPeriod.java:600/610` and `SabrMediaPeriod.java:370/380` put
  `representations.get(j).format` straight into `TrackGroup`, and `TrackSelectorManager.java:197-202`
  reads it back with `group.getFormat(trackIndex)`. Identity therefore survives the whole path, so an
  `IdentityHashMap<Format, ...>` built from the manifest that the player actually uses is a valid key.
- **none-state.** Jev called the supplied excerpts insufficient. Source trace: the "subtitles off"
  selection is `ExoFormatItem.fromSubtitleParams(null)`, whose fake `Format` has `language == null`;
  `SubtitleTrack.inBounds` calls `Helpers.startsWith(track2.format.language, trim(null) = null)` and
  `Helpers.startsWith(word, null)` returns `false` (`Helpers.java:794-807`), so no real track matches
  and `findBestMatch` keeps the empty auto track. `selectTrack` then calls
  `setSelection(renderer, -1, -1)`, which selects nothing, and the fallback in
  `TrackSelectorManager.java:257-269` makes `sortedTracks.first()` (format `null`) the selected track and
  sets `renderer.isDisabled = true`. Counterexample actually checked: a real subtitle `Format` always
  carries a non-null `language` (parser uses `sub.getName()` or `sub.getLanguageCode()`), while the
  empty track and the disabled state have `format == null`. Hence "subtitles off" is observable as an
  empty `SubtitleTrack`, and identity must not be inferred from a `SUBTITLE_NONE` value equality.
- **onTracksChanged-gap.** `TrackSelectionUtil.createTrackSelectionsForDefinitions` leaves `null` for a
  `null` definition (`TrackSelectionUtil.java:63-78`); `DefaultTrackSelector` builds a null definition
  when a renderer is disabled or has no override (`DefaultTrackSelector.java:1535-1548`);
  `TrackSelectionArray` documents that its array may contain null elements; and
  `ExoPlayerController.java:288-300` skips null selections. Turning subtitles off therefore produces no
  subtitle notification at all, so T02 must emit an explicit subtitle state.
- **url-uniqueness.** `DashManifestParser2.java:419/430-448` stores `sub.getBaseUrl()` unchanged as the
  representation base URL, and `TranslatedCaptionTrack.getBaseUrl()` / `PlayerResultExtensions.kt:41-53`
  append `&tlang=<code>`. Two entries with byte-identical URLs are the same source, so the binder must
  compare URLs and treat duplicates as ambiguous instead of taking the first candidate. The URL stays
  in memory and never enters logs or evidence.
- **id-only-insufficient.** A derived `tlang` track returns the origin's `vssId`
  (`TranslatedCaptionTrack.java:36-39`), and `ExoFormatItem.equals` for subtitles compares only the
  trimmed language (`ExoFormatItem.java:167-169`). id-or-name-only matching cannot split those tracks.
- **normalizer-equivalence.** Jev had only an abbreviated new-code excerpt, so it answered
  `insufficient`. This row is now decided mechanically instead: the test class contains a verbatim copy
  of the removed inline implementation and asserts equal output over 11 decoder-shaped cue streams
  (plain, VTT scrolling, TTML two-line, repeated text, empty cue, CJK, emoji). Command:
  `./gradlew.bat :common:testStbetaDebugUnitTest --tests com.liskovsoft.smartyoutubetv2.common.exoplayer.other.OriginalSubtitleNormalizerTest`
  -> BUILD SUCCESSFUL, 13 tests, 0 failed. This proves equivalence only for the covered streams; the
  input space is not exhaustively verified.
- **no-buffer-reset.** No caller of `OriginalSubtitleNormalizer.reset()` exists, `SubtitleManager.onCues`
  is the only normalize caller, and `ExoPlayerController.onSeekProcessed` only forwards `onSeekEnd`. The
  carried buffer is therefore a pre-existing latent behavior that T03/T05 must reset on seek and track
  change rather than a new regression.

## Design constraints accepted from this batch

1. Bind by `Format` identity against the manifest the player really uses; never by array order.
2. Require URL-level uniqueness; ambiguous or missing source -> no request, original subtitles + an
   explicit "source not identified" state.
3. Emit an explicit subtitle state on every track change, including off/empty.
4. Keep exactly one writer to the `SubtitleView`; do not copy the PR's `setCuesDirectly` second writer.
5. Reset the original-cue incremental buffer on seek/track/video change.
