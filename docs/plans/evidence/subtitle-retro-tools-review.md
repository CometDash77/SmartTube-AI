# Tool-use retrospective — 2026-09-20

Scope: this assistant's planning/documentation and GitHub investigation, not a fresh app-code review. Inputs contain bounded transcript excerpts/explicit summaries with tool-call locators; they are not invented source line numbers. Original conversation remains the primary evidence.

One live audit: `subtitle-retro-tools-jev-input.json` and `subtitle-retro-tools-jev-result.json`; model `typesafe/jev-1.13-20260917`, 2,376 input / 296 output tokens, 1,352.85 ms reported request time. Four relations met local thresholds; three required manual review. No retry. This reused the existing helper; no new integration, service or agent. No measured end-to-end efficiency gain is claimed.

| ID | Jev outcome | Final adjudication and action |
| --- | --- | --- |
| broad-search | supports .85, confidence .77 | Agree: unbounded source/fixture scan emitted 213,260 tokens before truncation. Use scoped source extensions and bounded excerpts; do not dump encoded player responses. |
| github-refetch | supports .96, confidence .94 | Agree: README fetched three times for different filters; retain once and filter locally. Two fetches were avoidable. |
| bad-query | contradicts .89, confidence .83 | Agree: four empty searches with malformed query text did not establish absence of a feature. Correct query syntax, then inspect official docs and actual maintainer comments. |
| write-check | supports .61, confidence .41 | Manual: concurrent write/check has no ordering guarantee. This proves a race risk, not that every check actually read stale data. Serialize mutation and validation. |
| replacement | supports .98, confidence .98 | Agree: single-line unbolded search did not match the wrapped bolded source. Command succeeded with no replacement, while commentary claimed success. Added exact-match assertions for the corrective edit and inspected the new state. |
| jev-recursion | supports .51, confidence .26 | Reject Jev tendency: summaries cannot independently verify code or establish that no useful local work remains. Prior claim of a blanket freeze was unjustified; export/CI work and evidence fixes can proceed independently. Do not use Jev to decide open-ended priorities. |
| clarification | supports .68, confidence .52 | Manual: asking logs versus subtitles versus both resolved a real ambiguity. Necessary question, but should have preceded subtitle-specific investigation. |

## Additional errors corrected without spending Jev calls

- Literal escaped CRLF text was accidentally inserted into Markdown and then repaired. A new read-only document checker catches this and trailing whitespace; it also validates daily section structure and JSON syntax.
- Daily next-step and blockers remained stale while newer changes were appended under them. Added a concise current summary and marked old blocks historical without deleting their entries.
- Acceptance matrix count lagged behind round 126 (351 versus 353); corrected by reading the log, not asking a model to count. Historical results are not fresh test runs.
- The initial audit result was printed but not persisted as a result file; its input remains under ignored tmp. Do not fabricate a raw result or rerun unchanged input to recover it. This audit saves both input and stdout result immediately.
- GitHub issue #6010's “About / sending logs” was explicitly a user's fork addition, not an official export feature. Logcat reports show community practice; they are not by themselves an official mandated procedure.
- “Only authenticated sources work” was an inference from empty responses, not a demonstrated general rule. Reader source attempts supported decoder formats; missing acceptance evidence does not globally disable them. Plan and matrix now distinguish these facts.
- Prior round 138 claimed a matrix change that did not occur. This review actually updates that matrix and retracts the blanket “all gates passed” wording. A paid API call is not reversible, and one successful call cannot verify 429 handling.

## Environment changes and limits

Added `scripts/check-development-docs.py`, a compact review/handoff guide linked by the progress README, and GitHub-only build pointers in the build and DSH skills. Kept root AGENTS.md lean and unchanged. No automatic hook is claimed. CI implementation, signing Secrets, exports and remote validation remain separate outstanding tasks; no local Gradle build or remote publication was performed.
