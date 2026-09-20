# SmartTube AI release maintenance

## Branches and upstream

- `master`: stable product source and the fork README.
- `production`: ongoing AI development.
- `upstream-mirror`: exact upstream `yuliskov/SmartTube` master commit, with no fork files or merge commits. The scheduled `Track upstream SmartTube` workflow runs every six hours on the default branch. It refuses non-fast-forward updates and never merges product branches automatically.
- Compare [pending upstream changes](https://github.com/CometDash77/SmartTube-AI/compare/master...upstream-mirror), review changes and submodules, merge into a working branch, and run CI before updating stable.

## Permanent signing identity

The public certificate SHA-256 is in [signing-certificate.sha256](signing-certificate.sha256). CI verifies every project-signed APK against this fingerprint.

GitHub repository Secrets hold `SIGNING_KEY` (base64 JKS), `KEY_STORE_PASSWORD`, `ALIAS`, and `KEY_PASSWORD`. The private keystore and credentials must never enter Git, artifacts or logs. Keep an offline backup of the maintainer's protected signing directory; GitHub Secrets cannot be downloaded as a recovery backup. Never regenerate this key for an ordinary release.

The distributed flavor remains `stbeta`, package `org.smarttube.beta`, with the visible name **SmartTube AI**. A stable GitHub release is independent of the Gradle flavor name. Existing upstream or temporary-debug installations of this package have a different certificate and require uninstalling before the first project-signed install (which clears their app data). Later releases must reuse this key and increase `versionCode`.

## Build and promote

1. Reserve the next stable `versionCode` and set `versionName` in `smarttubetv/build.gradle`. Development builds use the reserved number minus one; stable candidates use the reserved number itself.
2. Run `CI.yml` with `release_mode=true` on the exact reviewed commit. This fails if signing secrets are missing. JDK 11 common tests, JDK 17 release lint, APK assembly and signature verification must pass.
3. Review the candidate's APK metadata, checksums and certificate. Record device checks separately; CI does not verify TV interactions or live paid AI calls.
4. Promote the verified prerelease with `gh release edit <candidate-tag> --prerelease=false --latest --title <stable-title>`. Keep the tag and assets unchanged: the update manifest points to that immutable tag. Publish release notes documenting upstream revision, fork features, installation and known limitations.
5. The updater reads only `https://github.com/CometDash77/SmartTube-AI/releases/latest/download/smarttube-ai.json`. Each candidate contains this manifest, but prereleases are not selected by the latest-stable URL. Promotion makes the verified artifacts visible without rebuilding.

The manifest generator validates one APK per supported architecture; x86_64 selects x86, with the universal ARM build as the general fallback. SHA256SUMS includes basename-only entries usable from a download directory.

## Acceptance limits

The earlier AI feature baseline was reported accepted on TV (nightly-25 and nightly-27). A new upstream merge and permanent signature require new evidence; previous acceptance is not proof of the merged APK or same-signature device upgrades. Real paid AI calls and device-only storage/Android-version edges remain separate checks.
