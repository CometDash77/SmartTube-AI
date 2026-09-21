# Issue tracker: GitHub

Issues and PRDs for this repo live as GitHub issues in **CometDash77/SmartTube-AI**. Use the `gh` CLI for all operations.

## Repository targeting (this clone has several remotes)

`git remote -v` lists three remotes: `origin` = `CometDash77/SmartTube-AI` (this fork, where issues live), `upstream` = `yuliskov/SmartTube`, and `reference-kiss-translator` = `fishjar/kiss-translator`. With multiple remotes `gh` cannot infer the target reliably (a bare `gh run list` once showed the upstream repository's history here), so pass `-R CometDash77/SmartTube-AI` for issue, PR, release and workflow operations.

## Conventions

- **Create an issue**: `gh issue create -R CometDash77/SmartTube-AI --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> -R CometDash77/SmartTube-AI --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list -R CometDash77/SmartTube-AI --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> -R CometDash77/SmartTube-AI --body "..."`
- **Apply / remove labels**: `gh issue edit <number> -R CometDash77/SmartTube-AI --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> -R CometDash77/SmartTube-AI --comment "..."`

Project constraint: this repo's delivery path is GitHub-only (CI builds, lints, assembles and publishes prereleases), so issue work and the release workflow share the same repository. Never print credentials, keystore paths or secret values in issue bodies.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Skill default; set to `yes` here if this repo should treat external PRs as feature requests. `/triage` reads this flag.)_

This fork has no external-PR request flow; the app is built from this repository's own branches and validated by its own CI. If it is turned on later, PRs run through the same labels and states as issues, using the `gh pr` equivalents:

- **Read a PR**: `gh pr view <number> -R CometDash77/SmartTube-AI --comments` and `gh pr diff <number>` for the diff.
- **List external PRs for triage**: `gh pr list -R CometDash77/SmartTube-AI --state open --json number,title,body,labels,author,authorAssociation,comments` then keep only `authorAssociation` of `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR` or `NONE` (drop `OWNER`/`MEMBER`/`COLLABORATOR`).
- **Comment / label / close**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub shares one number space across issues and PRs, so a bare `#42` may be either — resolve with `gh pr view 42` and fall back to `gh issue view 42`.

## When a skill says "publish to the issue tracker"

Create a GitHub issue in CometDash77/SmartTube-AI.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> -R CometDash77/SmartTube-AI --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. `gh issue create -R CometDash77/SmartTube-AI --label wayfinder:map`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue (`gh api` on the sub-issues endpoint). Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's **native issue dependencies** — the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/CometDash77/SmartTube-AI/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where `<blocker-db-id>` is the blocker's numeric **database id** (`gh api repos/CometDash77/SmartTube-AI/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). GitHub reports `issue_dependencies_summary.blocked_by` (open blockers only — the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (`gh issue list -R CometDash77/SmartTube-AI --state open`, scoped to the map's sub-issues / task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh issue edit <n> -R CometDash77/SmartTube-AI --add-assignee @me` — the session's first write.
- **Resolve**: `gh issue comment <n> -R CometDash77/SmartTube-AI --body "<answer>"`, then `gh issue close <n>`, then append a context pointer (gist + link) to the map's Decisions-so-far.
