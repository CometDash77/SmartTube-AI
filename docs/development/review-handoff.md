# Reliable review and handoff

> Current subtitle status and next implementation scope: [current status](../plans/subtitle-current-status.md) and [production rework plan](../plans/subtitle-production-rework-plan.md). Historical plan sections below retain delivery constraints, not a claim that TV acceptance passed.

## Navigation and execution

- Current subtitle requirements and GitHub delivery policy: `docs/plans/deepseek-subtitle-dsh-ptc-plan.md`, sections 14–15. Build/test/lint/package/signature checks run in GitHub Actions; local source and documentation checks do not substitute for CI.
- Read the daily record's current summary before its historical rounds. Old observations are evidence, not current task instructions. Preserve history and label superseded status explicitly.
- Build/signing configuration: `.github/workflows/CI.yml`, `smarttubetv/build.gradle`, and `.agents/skills/smarttube-build/`. Do not print local.properties, keystores or secret values.
- Export reuse candidates: `BackupAndRestoreHelper.exportAppMediaFolder`, `MediaStoreFile`, and the subtitle timeline/cache. T13 remains planned until implementation evidence exists.

## Before reporting a change

1. Use apply_patch for exact edits. For scripted replacement, assert the expected old text occurs exactly once (or an explicitly justified count) before writing, reject identical old/new text, and check the new text afterward. Zero matches or unexpected counts must fail without writing. A command exit code cannot prove a replacement happened.
2. Keep dependent work sequential: write, read back, inspect diff, then validate. Only independent reads run in parallel.
3. Run `python -B scripts/check-development-docs.py <changed Markdown or JSON paths>` and `git diff --check -- <changed paths>`. The script checks formatting, daily section structure and JSON syntax; it does not prove factual consistency, privacy or acceptance.
4. Separate historical reported results, checks run now, and pending checks. Record failures and incomplete lint registries. APK assembly, signature validity, upgrade compatibility, prerelease availability and TV acceptance are distinct claims.
5. Keep one current next step. Do not require unrelated external prerequisites simultaneously or repeat tests without changed inputs/new evidence. A missing device does not prevent independent work.

## Efficient evidence collection

- Scope rg to production source extensions and relevant directories; exclude build output, fixtures and vendored data unless needed. Large response bodies can contain tokens and create avoidable truncation.
- Fetch GitHub once, retain the response, then print only relevant fields/excerpts. Distinguish maintainer guidance from user reports and fork features.
- Jev audits need atomic claims and actual bounded source excerpts with line ranges. Save exact input before calling and stdout result immediately; do not rerun an unchanged live request to recover a lost result. A summary supporting another summary is not implementation proof.
- Use Jev for several ambiguous semantic relations; use code for exact counts, paths and formatting. Never derive task priority or authorization from Jev. Review uncertainty manually.
- Clarify materially different outputs (subtitle files versus diagnostic logs) before specifying them. Do not add an interview or approval stage when repository evidence resolves the question.

## Required tool-use decisions

- Before fetching content already seen, identify a concrete reason: changed revision/file, missing excerpt, fresh remote status, or required read-before-edit. Otherwise reuse the retained result. No per-call narration or permanent cache infrastructure is required; keep sensitive responses out of tracked files.
- Start searches with the relevant directory, source extensions and bounded output. If output is truncated, refine the query or return paths/counts before selecting excerpts; do not dump the same payload with a larger output limit. Broaden only when narrow retrieval misses required evidence.
- For an unfamiliar connector/query format, validate one representative read-only query before fanning out. Follow the tool schema; do not embed scope arguments as invented query syntax. An empty result is inconclusive until query scope and syntax are established.
- Parallelize independent reads/checks only. A check against files being modified is dependent even when it uses another tool; await the write before reading back and validating. After an interrupted write, inspect the actual state before retrying.
- Match the evidence to the claim: implementation claims require source/callers and relevant test evidence; historical summaries establish only what was reported. Absence of a device, a search result or a passing test is not evidence that every independent task is blocked.
- Finish the edit and its verification before recording success. If validation fails or a replacement did nothing, correct the report and the artifact; do not append another success summary over an unresolved contradiction.

## Enforcement boundary

These rules govern Codex and DSH behavior but cannot intercept every tool call. The existing document checker automatically rejects the formatting/structure errors it detects when invoked; tests pin those checks. It does not enforce search economy, mutation ordering, replacement counts or truthfulness. Those require the explicit preconditions and read-back review above. Do not claim hooks or CI enforcement until actually installed and verified. No extra Jev call is needed for these deterministic checks.
