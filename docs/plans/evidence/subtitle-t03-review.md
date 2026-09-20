# T03 Jev audit and manual adjudication

Date: 2026-09-20, Asia/Hong_Kong. Scope: the background native-subtitle snapshot used by the AI
prefetch path (timeline, timing base, decoder waiting, clearing frames, MIME risk).

## Actual call

```powershell
python -B .agents/skills/smarttube-jev-triage/scripts/jev.py audit tmp/jev-subtitle-t03-input.json --live
```

- Mode `audit`, status `judged`, exactly 1 call, no retry, no fallback.
- Model `typesafe/jev-1.13-20260917`; 2,588 input / 218 output tokens; cost 0.00010869.
- `input_sha256` `7c4cfbbd7a9c4bc8439e7fda39f04eda8515ac63ad1ed0daabbfda66e19de8ab`.
- Input [subtitle-t03-jev-input.json](subtitle-t03-jev-input.json), raw result
  [subtitle-t03-jev-result.json](subtitle-t03-jev-result.json). 5 checks.

Thresholds: confidence >= 0.70, winner >= 0.85, margin >= 0.20. **3 of 5 rows passed**; 2 were
resolved manually against the source.

| id | Jev answer | probability / confidence | Above thresholds | Resolution |
| --- | --- | --- | --- | --- |
| `time-base` | supports | 0.80 / 0.69 | no (confidence) | **supports** by source reading |
| `async-decoder` | supports | 0.92 / 0.88 | yes | accepted |
| `duplicate-event-times` | supports | 0.88 / 0.81 | yes | accepted |
| `empty-cue-clears` | supports | 0.92 / 0.88 | yes | accepted |
| `mime-mapping` | insufficient | 0.97 / 0.96 | (evidence gap) | **kept as an explicit open risk** |

## Manual adjudication

- **time-base.** `SubtitleOutputBuffer.setContent` resolves
  `subsampleOffsetUs == Format.OFFSET_SAMPLE_RELATIVE` to the buffer's own `timeUs`;
  `getEventTime` adds it and `getCues` subtracts it. `Format.OFFSET_SAMPLE_RELATIVE` is
  `Long.MAX_VALUE` and this app's subtitle formats keep that default, so decoding with
  `input.timeUs = 0` yields file-relative times equal to media time, with the offset applied once.
  The reader test now asserts snapshot text at boundary-1 / boundary / boundary+1 against an
  independent native decode of the same bytes.
- **mime-mapping (open risk, not a claim we can accept).** `SubtitleDecoderFactory.DEFAULT` maps
  `application/x-mp4-vtt` to `Mp4WebvttDecoder`, which parses MP4 `vttc` boxes, while the YouTube
  timedtext endpoint returns text. Whether a real SmartTube source declares that MIME for a plain
  `WEBVTT` payload is unknown in this workspace. T10 must capture one real source (MIME + first
  bytes) before this is called supported; until then the reader correctly reports
  `DECODE_FAILED`/`UNSUPPORTED_FORMAT` and the AI bypass stays off for that source.

## Consequences applied to the code

1. The reader polls `dequeueOutputBuffer()` with a deadline, cancellation and a bounded grace
   period instead of stopping at the first null output.
2. The builder collapses events that share one microsecond into a single frame, matching
   `TextRenderer`'s "advance past every event at or before the position" behaviour and preventing
   the incremental text rule from being applied twice.
3. Empty cue lists stay in the timeline as real clearing frames.
4. Bounds (2 MiB / 20,000 events / 30 s) are enforced as controlled refusals; a cancelled or
   oversized snapshot is never presented as a usable timeline.
5. No URL, subtitle text or credential is written to any log or evidence file by this code.
