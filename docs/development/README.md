# Local development progress

Status: ready-for-agent; implemented by the initial daily-record change.

## Problem and solution

New sessions need a durable account of local development without relying on conversation history. This directory is the single home for daily progress records. Every session must read this document and the latest daily record before planning or development, then consult older records relevant to its task.

## User stories

1. As a project maintainer, I want every new session to read the recorded progress, so that work resumes with the correct context.
2. As a contributor, I want to record completed work and verification as work progresses, so that later sessions can distinguish results from intentions.
3. As a contributor, I want all sessions on the same date to update one daily record, so that the day's progress is not fragmented.
4. As a contributor working across midnight, I want unfinished work carried into the next day's record, so that ongoing tasks remain visible.
5. As a contributor, I want missing or stale history identified explicitly, so that unverified assumptions are not presented as facts.
6. As a contributor sharing a workspace, I want existing entries preserved when I update the record, so that another session's work is not lost.

## Recording contract

- Name each daily file exactly `YYYY-MM-DD.md`, using `Asia/Hong_Kong` (UTC+08:00). One file represents one date and contains all tasks and sessions for that date. This README is the permanent specification, not a daily record.
- Select the latest record by the date in its filename, not filesystem modification time. Read today's record too if it exists. Review relevant older entries when following an unfinished task.
- If there is no daily record, create today's file before development. If today has no file, create it when beginning work and carry forward relevant unfinished work from the latest record. Do not reconstruct undocumented history as fact.
- Use these sections in every daily file: `Scope`, `Progress`, `Verification`, `Outstanding work and blockers`, and `Next step`. Identify the working branch. Add time-stamped entries under `Progress` when multiple milestones or sessions occur.
- Record changed areas, decisions and their reasons, checks with observed outcomes, and any uncommitted work or limitations needed for a handoff. Label proposed, in-progress, completed, deferred and cancelled work accurately.
- Update after meaningful milestones, scope changes and verification results, and before a session ends or hands off. An interrupted session may leave stale entries: the next session must reconcile them with Git status and actual files.
- Re-read a file immediately before editing it; preserve existing entries and resolve concurrent edits without overwriting another session's work. Correct mistakes explicitly rather than silently erasing history.
- At midnight, use the new date's file for subsequent work and carry forward open items. Do not rename the previous day's record.
- Never include credentials, private tokens or sensitive logs. A record is evidence and context, not permission to resume a cancelled task or perform an external action.
- Include relevant daily-record updates with development commits. A commit hash may be added by a later update; do not amend recursively just to insert the current commit's own hash.

## Verification and acceptance

- The root `AGENTS.md` links to this document and mandates reading and updating records.
- Daily filenames use real ISO calendar dates and are unique per date; all same-day work stays in that file.
- Required sections are present, links resolve, and recorded outcomes match the actual checks.
- Inspect Git diff and run `git diff --check` for documentation changes. No Android build or new test framework is required for this convention.
- This is an agent workflow requirement, not an automatic session-start hook; compliance must not be described as technically enforced by the application.

## Out of scope

No subtitle implementation, historical progress reconstruction, issue-tracker setup, external publication, automated session hooks, branch merging, or remote Git operations. This change establishes and documents the local recording convention only.
