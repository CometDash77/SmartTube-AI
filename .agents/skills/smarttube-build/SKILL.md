---
name: smarttube-build
description: Build or validate SmartTube Android changes, diagnose Gradle setup, or prepare APKs.
---

# SmartTube build and validation

Current project policy: run compilation, Gradle tests, lint, packaging and APK signature verification in GitHub Actions, and deliver test APKs through GitHub prereleases. Local work is source/static/document checks only. Follow plan section 15 in `docs/plans/deepseek-subtitle-dsh-ptc-plan.md`; historical local-build examples are not permission to run builds locally. Remote signing configuration and device acceptance must be established separately.

Use the repository wrapper and resolve the actual shared-module roots before choosing tasks. Match validation to the changed module and requested variant; preserve pinned dependencies and existing signing configuration.

Read [references/build.md](references/build.md) for environment setup, Gradle commands, CI parity, and signing/device boundaries. Documentation-only work does not need this workflow.

Complete when relevant checks have passed and the requested artifact exists, or identify the precise environment blocker and complete independent checks. Distinguish compilation, tests, APK creation, signing, and device verification in the result.
