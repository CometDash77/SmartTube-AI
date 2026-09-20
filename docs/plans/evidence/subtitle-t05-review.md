# T05 Jev audit and manual adjudication

Date: 2026-09-20, Asia/Hong_Kong. Scope: the AI subtitle session identity and stale-write blocking in
`AiSubtitleController`.

## Actual call

```powershell
python -B .agents/skills/smarttube-jev-triage/scripts/jev.py audit tmp/jev-subtitle-t05-input.json --live
```

- Mode `audit`, status `judged`, exactly 1 call, no retry, no fallback.
- Model `typesafe/jev-1.13-20260917`; 1,953 input / 178 output tokens; cost 0.000082026.
- `input_sha256` `4a906abe3609...`. Input [subtitle-t05-jev-input.json](subtitle-t05-jev-input.json),
  raw result [subtitle-t05-jev-result.json](subtitle-t05-jev-result.json). 4 checks.

Thresholds: confidence >= 0.70, winner >= 0.85, margin >= 0.20. **3 of 4 rows passed**; one was
resolved manually against the source.

| id | Jev answer | probability / confidence | Above thresholds | Resolution |
| --- | --- | --- | --- | --- |
| `invalidate-before-cancel` | supports | 0.97 / 0.97 | yes | accepted |
| `same-source-idempotent` | supports | 0.91 / 0.87 | yes | accepted |
| `stale-refused` | supports | 0.92 / 0.87 | yes | accepted |
| `mode-changes-pure` | supports | 0.59 / 0.39 (contradicts 0.13) | no | **supports** after re-reading the source |

## Manual adjudication

- **mode-changes-pure.** `setDisplayMode` only assigns `mDisplayMode` and calls
  `mDisplay.setAiDisplayMode(mode)`; it touches no generation counter and cancels nothing.
  `setAiEnabled(true)` re-applies the stored `mDisplayMode`, which is what the "remember the mode
  while AI is off" test asserts. The 0.13 contradiction mass was checked against the actual call
  sites: the only invalidation in that area belongs to `setAiEnabled(false)`, which is intended.

## Remaining verification gap for T05

The controller core and its unit tests are complete, but the player-side wiring is **not** done yet:
nothing in `PlaybackFragment`, `PlayerPresenter`/`PlayerEngine` currently creates the controller,
feeds it `onSubtitleSourceSelected`/`onSeek`/`onVideoLoaded`/`onEngineReleased`, or calls
`SubtitleManager.resetOriginalCueState()` on seek and track change. Engine rebuild/rotation, repeated
CC presses on a device and elimination of residual timers therefore remain unverified and are the
next step of T05.
