# Stable 32.54 integration review

## Scope and revisions

- Fork baseline: `10aee0b4`; merged upstream: `4a786a8f` (32.54).
- Merge commit: `20c37054`; release implementation: `e7363590`.
- SharedModules remains `13f5687d`; MediaServiceCore advances from `9df453a0` to `8b4a884b`. Git merge-base confirms the former is an ancestor of the latter, so no fork submodule commits were discarded. The merge conflict arose because the superproject's original base gitlink (`59795dfe`) was newer than its fork baseline gitlink.
- Build selects in-repository submodules because neither sibling checkout exists.

## Conflict resolution and compatibility

The upstream superproject delta is limited to SimpleMediaItem, VideoMenuPresenter, the MediaServiceCore gitlink and version metadata. The new MediaItem interface method is implemented by SimpleMediaItem; the feedback menu supports both existing feedback tokens and new engagement-panel endpoints. MediaServiceCore also restores the news-shorts filter change from the original base. Its changed areas are browsing/feedback models and service data; subtitle/player transport files are unchanged.

The version conflict preserves the fork's reserved stable version policy with versionName 32.54 and versionCode 2444. AI subtitle implementation and tests are byte-identical to the accepted fork baseline, as verified by an empty Git diff scoped to `common/.../exoplayer/other` production and test directories. This is evidence of preservation, not proof that a new service dependency cannot affect runtime behavior; CI and device evidence remain distinct.

## Jev audit and manual adjudication

One live audit, four atomic checks, model `typesafe/jev-1.13-20260917`; 3,897 input / 171 output tokens, 1,210.6 ms. No retry or service fallback. Exact [input](stable-32.54-jev-input.json) and [result](stable-32.54-jev-result.json) are retained with source paths, line numbers and revisions.

| Check | Jev result | Astra adjudication |
| --- | --- | --- |
| Existing feedback-token fallback | supports .99 / confidence .99 | Supported by VideoMenuPresenter's null-endpoint branch calling the retained reasons flow. Does not prove live server responses. |
| Preferred update host | supports .97 / confidence .95 | Supported: AppUpdateChecker only sorts supplied URLs. A stored preference cannot add an upstream URL. |
| Manifest/parser compatibility | supports .94 / confidence .91 | Supported: package downloadUrl and ABI arrays match AppVersionChecker; integer versionCode matches comparison. Four contract tests additionally cover ABI routing, missing/duplicate files and wrong repository. |
| Same-timeline planner preservation | supports .61 / confidence .43; manual review required | Supported after source/test review: AiSubtitleSessionBinder lines 377–397 guards dispatcher.setTimeline with reference identity change. SubtitleRetranslationTest lines 194–222 exercises unchanged configuration and asserts no second request. Real CI execution is recorded separately. |

## Release isolation

The distributed stbeta resource has one update URL in this fork's latest stable release. The unused ststable flavor has no upstream feed. Generated manifests point at the candidate's fixed tag and become active only when that candidate is promoted to latest stable. The permanent signing certificate is pinned in CI; private signing material is outside Git and configured only as repository Secrets.

The pristine `upstream-mirror` branch points at upstream master. Its scheduled workflow performs a normal, fast-forward-only push; rewritten history or accidental product commits fail safely. It does not merge into master/production or publish releases.

## Verification boundary

Local manifest tests: 4 passed. YAML parse and fork documentation checks passed. GitHub run [35517965667](https://github.com/CometDash77/SmartTube-AI/actions/runs/35517965667) for `e7363590` passed 68 suites / 558 tests (zero failures/errors/skips), both release lint tasks and APK assembly. Android signature verification passed, but the new certificate pin parser falsely rejected SDK output using `V2 Signer:` instead of the older `Signer #1` label. No release was published by this failed run.

The actual logged certificate fingerprint equals the pinned project certificate. Replaying that line reproduced the old parser failure and passed the replacement parser. Four new identity tests cover old/new SDK labels, missing certificate, public-key-vs-certificate confusion and wrong/additional signers; all pass. CI uses the tested parser and still runs apksigner verification before parsing. This deterministic parsing repair needs no second Jev batch. A fresh release run remains required.

The upstream README is archived verbatim, including historical Markdown trailing spaces; do not misreport those preserved bytes as newly introduced product whitespace errors. Changed fork files passed the CR-aware whitespace check before archival addition.

No new TV/device or paid AI call was performed. Previous user acceptance of nightly-25/nightly-27 does not certify this merged APK, the permanent-key first install or same-key on-device upgrade.
