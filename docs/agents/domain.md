# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Layout for this repository: multi-context

This repository is a **multi-context** repo: there is no single global glossary. The domain documentation is expected as a root `CONTEXT-MAP.md` that points at one `CONTEXT.md` per subproject context.

The in-repo subprojects that will own a context file are the ones carrying this project's own domain language:

- `smarttubetv/` — the TV application (player UI, remote/focus behavior, playback surfaces).
- `common/` — shared application code (subtitles, exports, preferences, controllers).

External code lives in the Git submodules `SharedModules/` and `MediaServiceCore/` (upstream projects); they are not domain contexts of this repository and get no `CONTEXT.md` here.

**Nothing has been created yet.** This setup records the layout only: there is currently no `CONTEXT-MAP.md`, no per-subproject `CONTEXT.md` and no `docs/adr/` directory. They are created lazily by `/domain-modeling` the first time a term or an architectural decision is actually resolved — that first write creates the root `CONTEXT-MAP.md` (pointing at the context files) before or together with the context file it belongs to.

## Before exploring, read these

- `CONTEXT-MAP.md` at the repo root, if it exists — it points at one `CONTEXT.md` per context. Read each one relevant to the topic.
- `CONTEXT.md` at the repo root, if it exists (single-context fallback).
- `docs/adr/` — read ADRs that touch the area you are about to work in. In this multi-context repo also check `<context>/docs/adr/` (for example `common/docs/adr/`) for context-scoped decisions.

If any of these files do not exist, **proceed silently**. Do not flag their absence and do not suggest creating them upfront. `/domain-modeling` (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them when terms or decisions actually get resolved.

## File structure

```
/
├── CONTEXT-MAP.md          ← created on the first resolved term
├── docs/adr/               ← system-wide decisions (when they exist)
├── smarttubetv/
│   ├── CONTEXT.md          ← TV app context (when it exists)
│   └── docs/adr/
└── common/
    ├── CONTEXT.md          ← shared application context (when it exists)
    └── docs/adr/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in the relevant `CONTEXT.md`. Do not drift to synonyms the glossary explicitly avoids.

If the concept you need is not in the glossary yet, that is a signal: either you are inventing language the project does not use (reconsider), or there is a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders) — but worth reopening because…_
