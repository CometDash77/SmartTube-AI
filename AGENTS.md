# SmartTube-AI

## Mandatory session progress record

- At the start of every new session, before planning or development, read `docs/development/README.md` and the latest dated progress file in `docs/development/`. Also read today's file if it exists and any older entries relevant to the task. If no dated file exists, create today's record before development; never invent missing history.
- Record all local development progress in `docs/development/YYYY-MM-DD.md`, using the calendar date in `Asia/Hong_Kong` (UTC+08:00). There must be exactly one Markdown progress file per recorded date. Reuse that file across sessions and tasks; do not create date suffixes, per-session files, or duplicate daily logs elsewhere.
- Update the daily record after meaningful milestones or changes of direction and before handing off or ending a session. Include scope, completed work, verification evidence, outstanding work/blockers, and the next step. Distinguish plans from completed work and automated checks from device verification; never record secrets.
- Before updating a record, re-read it and preserve other sessions' entries. At a date rollover, continue in the new day's file and carry forward outstanding work. Progress records provide context, not authorization: reconcile them with the current repository and latest user instructions.

## Project engineering rules

- Follow `docs/development/review-handoff.md` for tool use and evidence: reuse unchanged reads, scope searches, and serialize modification → read-back/diff → validation → reporting. Scripted replacements must fail on unexpected match counts; never report a change based on command success alone. These are agent obligations, not runtime-enforced hooks.

- This is an Android TV Gradle project. App code is in `smarttubetv/`, shared application code in `common/`; preserve TV remote/focus behavior and existing Android compatibility constraints.
- `SharedModules/` and `MediaServiceCore/` are Git submodules. `settings.gradle` prefers sibling checkouts (`../SharedModules`, `../MediaServiceCore`) when present. Before editing shared code, establish which checkout the build actually uses; keep unrelated submodule revisions intact.
- Use the checked-in Gradle wrapper. CI uses JDK 17 and the `stbeta` release variant; dependency and SDK versions come from the selected SharedModules constants. For build setup, task selection, signing, or APK verification, use `.agents/skills/smarttube-build/SKILL.md`.
- Complete the requested change and relevant verification, including in-scope fixes. A document-only change needs document/config checks, not an APK build. For app changes, use affected tests and compilation; release/CI changes require the relevant CI checks. State unavailable device checks separately from automated results.
- A generic skill does not require a new design approval, issue-tracker setup, commit, full-suite run after each edit, or a review pause in this repository. Preserve explicit task checkpoints and actual release/signing/device authorization. Do not import approval gates or completion claims from another SmartTube checkout or another project.
- Proactively integrate TypeSafe/Jev into substantial code inspections and code reviews (for example, cross-module changes, several interacting lifecycle paths, or broad branch/PR reviews). Do not wait for the user to request Jev. Load `.agents/skills/smarttube-jev-triage/SKILL.md`, identify useful bounded judgments, and attempt a live batch when applicable: `rank` for 8+ ambiguous retrieved code/context snippets; `audit` for several claim/evidence pairs needing semantic verification. Do not pad candidate lists, manufacture ambiguity, or send deterministic checks to Jev merely to record a call. Small, obvious reviews may remain manual; if a substantial review has no suitable batch or the service is unavailable, briefly record why and continue the review.
- Preserve source paths, line numbers, original evidence, and actual Jev inputs/results with the review materials; exclude secrets and unrelated private data. Astra designs the questions, checks counterexamples, and resolves uncertainty, insufficient evidence, or conflicting judgments against the source. On service failure, immediately fall back to manual review without retrying unchanged input or adding a user-approval gate. Report actual calls, fallbacks, and remaining verification gaps; do not assume efficiency gains. Jev assists prioritization and evidence checks, never replaces code review, implementation, tests, or device acceptance.
- Route explicit skills and obvious build work directly. Use code for paths, shared-checkout selection, counts, versions, exit codes and artifact/signature checks; use Astra for research, implementation, complex reasoning and generation. Jev must not authorize actions, switch models/spawn agents, discard governing instructions, or certify task completion. Keep actual test and device evidence authoritative.
- For Jev work, Astra first probes assumptions and decomposes broad judgments into precise, evidence-backed atomic questions (the skill's Grill-the-judgment method). Batch independent questions against the same state; sequence only genuine evidence dependencies. This is question design, not a mandatory user interview.

## Tool-use hygiene (from the 2026-09-20 self-audit)

Measured evidence and per-item verdicts: `docs/research/agent-tool-use-audit-2026-09-20.md` (40 atomic assertions, one live Jev audit). These are agent obligations, not runtime-enforced hooks.

- **Scope reads before editing.** `grep` for the anchor, then read with `offset`/`limit`; read a large file in full only once, right before the edit that needs the observation guard. For progress and report files, read the structure and the newest section instead of the whole history — oversized reads were 76% of this session's truncated model-visible output.
- **Decide line endings before the first edit.** Check `git ls-files --eol <path>`; write added lines as LF and never let an edit rewrite a file's overall line endings (that produced 89 fake changed lines once). Re-check with `git diff --check` and `git diff --numstat` after the first edit of a CRLF or mixed file.
- **Re-read after any non-edit writer.** A script, formatter or generated log that rewrites a file invalidates the harness observation: read again before the next edit instead of assuming the file is unchanged.
- **Copy `old_string` from the bytes just read.** Do not rebuild it from grep output, trimmed text or memory; indentation and CJK quote forms are part of the anchor.
- **Read the whole failure, not the status line.** On a failed build or test, write the complete log to a file and read the diagnostics back (`\.java:\d+`, `error:`); never diagnose from a filtered BUILD line alone. Batch same-module edits into one verification wave instead of one build per small edit.
- **Keep literals safe in code-executed tool programs.** Inside a PTC / `run_code` program, use 「」 or U+201C for quoted prose and never place a backtick inside a template literal; build strings by concatenation. A parse failure resends the whole program, so check quoting before sending.
- **Stage explicitly.** Prefer explicit paths over `git add -A` in a shared workspace.
- **Record the Jev decision.** In each implementation round, state whether a Jev batch was used and why not when it was skipped; a silent skip is not reviewable afterwards.

## DeepSeek Harness compatibility (secondary adapter)

- Astra/Codex remains the primary workflow and source of truth. This section only exposes a compatible entry point for DeepSeek Harness; it does not replace or weaken the rules above.
- DeepSeek Harness loads this root `AGENTS.md` plus nested `AGENTS.md` files and discovers project skills under `.agents/skills/*/SKILL.md`. Use the `smarttube-deepseek-ptc` skill when running through DeepSeek Harness or when the task is explicitly using PTC.
- The adapter keeps model-facing context compact: give the task, relevant paths, and acceptance checks first; let PTC perform bounded search/filter/aggregation/state bookkeeping in code; return only concise evidence and failures to the model.
- In PTC mode, call tools through the generated SDK bindings inside `run_code`; do not narrate a simulated tool loop or call `run_code` recursively. Preserve the same read-before-edit, validation, Android compatibility, and Astra-first priorities.
- The adapter is intentionally additive. Codex/Astra may ignore this section and continue loading the existing skills normally.

## Agent skills

### Issue tracker

Issues and PRDs live as GitHub issues in `CometDash77/SmartTube-AI` (external PRs are not a triage surface; every `gh` operation passes `-R CometDash77/SmartTube-AI`). See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles map one-to-one to `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human` and `wontfix`; the wayfinder labels are `wayfinder:map|research|prototype|grilling|task`. See `docs/agents/triage-labels.md`.

### Domain docs

Domain docs use a **multi-context** layout — a root `CONTEXT-MAP.md` pointing at one `CONTEXT.md` per subproject (`common/`, `smarttubetv/`) — and none of those files exist yet. See `docs/agents/domain.md`.
