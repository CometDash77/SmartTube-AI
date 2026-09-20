# T04 Jev audit and manual adjudication

Date: 2026-09-20, Asia/Hong_Kong. Scope: the single write entry to the subtitle view, recomposition
without re-normalization, clearing, fallback and mode purity.

## Actual call

```powershell
python -B .agents/skills/smarttube-jev-triage/scripts/jev.py audit tmp/jev-subtitle-t04-input.json --live
```

- Mode `audit`, status `judged`, exactly 1 call, no retry, no fallback.
- Model `typesafe/jev-1.13-20260917`; 2,344 input / 215 output tokens; cost 0.000098448.
- `input_sha256` `0c132936af5e...`. Input [subtitle-t04-jev-input.json](subtitle-t04-jev-input.json),
  raw result [subtitle-t04-jev-result.json](subtitle-t04-jev-result.json). 5 checks.

Thresholds: confidence >= 0.70, winner >= 0.85, margin >= 0.20. **4 of 5 rows passed**; one was
resolved manually with a mechanical check.

| id | Jev answer | probability / confidence | Above thresholds | Resolution |
| --- | --- | --- | --- | --- |
| `single-writer` | supports | 0.84 / 0.77 | no (winner 0.84) | **supports** by mechanical check |
| `no-renormalize` | supports | 0.89 / 0.85 | yes | accepted |
| `empty-clears` | supports | 0.99 / 0.98 | yes | accepted |
| `fallback-no-placeholder` | supports | 0.99 / 0.98 | yes | accepted |
| `mode-purity` | supports | 0.90 / 0.84 | yes | accepted |

## Manual adjudication

- **single-writer.** A grep of `SubtitleManager.java` lists `setCues` exactly twice: the
  `CueSink` interface declaration (line 40) and the single call `mCueSink.setCues(cues)` inside
  `renderCurrent()` (line 150). In production `mCueSink` is `subtitleView::setCues` (line 72).
  `mOriginalSubtitleNormalizer` is referenced only from `onCues` (line 90) and
  `resetOriginalCueState` (line 137), so composed bilingual text can never re-enter the
  normalization. This matches the plan's decision not to copy the upstream PR's second,
  listener-bypassing `setCuesDirectly` writer.

## Consequences confirmed for later tasks

1. One writer only; T05 can invalidate state at the controller level without adding another path to
   the view.
2. Translation arrival and mode changes are pure recomposition: no fetch, no re-normalization, so
   T06's scheduler can call `setTranslations` freely on the main thread.
3. Clearing is driven by the original frame, not by translation state: a stale translation can never
   keep a cleared subtitle on screen.
4. Missing or blank translations always fall back to the original line; no placeholder text exists
   to be mistaken for a translation.
