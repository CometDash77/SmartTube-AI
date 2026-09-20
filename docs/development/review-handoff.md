# Reliable review and handoff

## Navigation and execution

- Current subtitle requirements and GitHub delivery policy: `docs/plans/deepseek-subtitle-dsh-ptc-plan.md`, sections 14–15. Build/test/lint/package/signature checks run in GitHub Actions; local source and documentation checks do not substitute for CI.
- Read the daily record's current summary before its historical rounds. Old observations are evidence, not current task instructions. Preserve history and label superseded status explicitly.
- Build/signing configuration: `.github/workflows/CI.yml`, `smarttubetv/build.gradle`, and `.agents/skills/smarttube-build/`. Do not print local.properties, keystores or secret values.
- Export reuse candidates: `BackupAndRestoreHelper.exportAppMediaFolder`, `MediaStoreFile`, and the subtitle timeline/cache. T13 remains planned until implementation evidence exists.

## Before reporting a change

1. Use apply_patch for exact edits. For scripted replacement, assert the expected old text occurs exactly once and check the new text afterward. A command exit code cannot prove a replacement happened.
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
