# Planning review evidence

Date: 2026-09-19, Asia/Hong_Kong. Scope: source-backed planning only. No application implementation, Gradle build, device acceptance, or authenticated DeepSeek request was performed.

## Sources and actual calls

- Full user-supplied research was read, including both previous Jev audit summaries and the remaining verification matrix. The matching repository report is `docs/research/smarttube-subtitle-translation.md`.
- Local baseline: `1fbcc1a6cbb87740bbb9ae76d4ed90781ffe45a1`. Active shared roots were the in-repository `SharedModules` and `MediaServiceCore`, at `13f5687dd6757b02fbcdf14c5403d0339e377db5` and `9df453a0b13d593452b4817d778645916b978236`. Existing uncommitted documentation was preserved.
- Upstream PR #5839 metadata, all 19 file patches (one mode-only wrapper entry has no textual patch), and its issue comment were fetched through the GitHub connector. The added source files were reconstructed from complete new-file patches under ignored `research-sources/smarttube-pr-5839/`; patches were not applied to the application. Head: `71fe9a53d9008cbc11614334844ebc5df562eb09`, open and unmerged at retrieval.
- Issue #2889 and all six returned comments were read; its stale closure was distinguished from feature completion. Issue #1908 comments, related issue bodies, PR #5454/#4402/#6202 metadata and bodies, and Discussion #5779 were read. Discussion #6195 was inspected and excluded as a release announcement, not a subtitle implementation.
- The web-search tool failed because the configured proxy did not support its search endpoint. Public GitHub connector reads and direct HTTP reads of primary sources succeeded; this was not a Jev failure. Discussions searches used subtitle, dual and translation. Bounded searches do not prove that no other fork has an implementation.
- DeepSeek official model and API pages were read directly. They identify `deepseek-flash` as DeepSeek-V4.1-Flash and document non-thinking / non-streaming / JSON output. This corrects a stale model note in the local DSH skill without changing that skill in this planning task.
- DSH official repository default branch is `master`; initial raw reads using `main` returned 404. Correct-branch PTC note, runtime README, execution pipeline and tool catalog were then read. Generated runtime SDK remains authoritative at execution time; no DSH process was launched here.

## Live Jev audit

Command:

```powershell
python -B .agents/skills/smarttube-jev-triage/scripts/jev.py audit docs/plans/evidence/subtitle-plan-jev-input.json --live
```

Actual execution: one native typed service request, five independent claim/evidence pairs, input file 16,149 UTF-8 bytes. `status=judged`, `calls=1`, model `typesafe/jev-1.13-20260917`. No service failure, repeated-input retry, or rank call. Rank was unnecessary because the relevant symbols and files were already identified.

| Claim ID | Result | Manual disposition |
| --- | --- | --- |
| identity | supports .94, confidence .91 | Confirmed source: current DASH parser passes vssId; TranslatedCaptionTrack returns the original vssId for translated tracks. Reuse the ID as a candidate key, never as a uniqueness guarantee. The Kotlin implementation was also read and has the same reuse behavior. |
| disabled | supports .96, confidence .94 | Confirmed the onTracksChanged loop emits only for non-null selections. Plan requires explicit text-NONE state and tests, including non-manual changes. |
| postprocess | supports .63, confidence .45; below threshold | Astra manually checked the full forceCenterAlignment function and onCues caller. When subsBuffer contains the original line and the next input is original + newline + translation, the TTML branch can replace the original and newline. Therefore translations must be composed after original normalization, without re-entering its stateful logic. This is a static counterexample, not a device reproduction. |
| pr_protocol | supports .99, confidence .98 | Confirmed against the full PR SubtitleTranslator and GoogleTranslateService sources: current processed cues trigger per-text work, unlike the proposed stable-ID context batches. UI/hook/merge concepts are reusable; the transport/scheduler is not the desired DeepSeek implementation. |
| native_timing | supports .97, confidence .96 | Confirmed Subtitle event boundaries and SubtitleOutputBuffer subsample offsets. Plan explicitly requires interval/offset parity checks rather than assigning fabricated Cue timestamps. |

Raw input and output are preserved beside this document. The raw result's `not_checked` explicitly excludes evidence authenticity/freshness, code correctness and overall task completion. No performance or token-saving claim is inferred from these calls.

## Review decisions that changed the plan

1. The prior report's numeric XML MPD representation warning must not be generalized to the regular DASH/SABR parser paths. However, the PR's first-vssId-match utility is also unsafe for synthetic translated tracks. Task T02 tests both facts together and preserves source identity through the actual manifest/track path.
2. Existing SubtitleView multiline rendering is sufficient for the required three display modes. A second view and PR #5839's customized green/italic Painter are not needed for this scope.
3. onCues alone cannot supply future subtitles. The chosen first implementation reads the exact selected subtitle once in a bounded worker and uses the existing native decoder. Decoder interception is an evidence-triggered replacement option, not a second planned subsystem.
4. ASR normalization needs a shared, independently owned state model for prefetch and display, with seek seeding and exact raw/processed matching. Approximate text/time lookup from the PR is not an acceptable shortcut.
5. Cancellation and stale checks apply to failures as well as successes; retry counters must survive tick/eviction and cancellation must not create hidden concurrency.
6. For API 17–22, session-only Key storage avoids plaintext fallback and custom legacy cryptography. API 23+ Keystore/no-backup persistence requires actual Android tests. This is a stated first-version product choice, not an already verified implementation.

## Final planning verification

The plan's local links, line anchors, JSON artifacts and source excerpts are checked mechanically before delivery. Task dependencies, requirement coverage and the distinction between planned tests and actual planning checks are reviewed manually. The actual final check results are recorded in the single daily development record.
