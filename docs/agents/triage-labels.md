# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker (GitHub, `CometDash77/SmartTube-AI`). Every label in the right-hand column exists in the repository; do not invent new ones per issue, and apply no label beyond this file and the wayfinder set unless the user asks.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (for example "apply the AFK-ready triage label"), use the corresponding label string from this table.

Notes for this repository:

- `wontfix` is one of GitHub's default labels and already existed before this setup; the other four triage labels were created by the setup command.
- Pre-existing project labels stay untouched by triage: `duplicate`, `bug`, `enhancement`, `documentation`, `question`, `help wanted`, `good first issue`, `invalid`, `accessibility`.
- The wayfinder labels (`wayfinder:map`, `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling`, `wayfinder:task`) are not triage roles; `/wayfinder` applies them (see `docs/agents/issue-tracker.md`).
- Label operations go through the `gh` CLI and must target this fork explicitly (`-R CometDash77/SmartTube-AI`), because the clone has several remotes.
