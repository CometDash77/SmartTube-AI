# kiss-translator research snapshot

## Scope and status

Downloaded and organized on 2026-09-19 (Asia/Hong_Kong). This is acquisition evidence, not a completed architecture review or subtitle implementation. No upstream scripts were run or dependencies installed.

## Provenance

- Repository: [fishjar/kiss-translator](https://github.com/fishjar/kiss-translator).
- Requested reference: `dev`, `3d03f21c`.
- Full commit: [3d03f21c50536d67a5ca457e0181f8cd4911b781](https://github.com/fishjar/kiss-translator/commit/3d03f21c50536d67a5ca457e0181f8cd4911b781).
- GitHub commit timestamp: `2026-09-19T08:09:29Z`.
- [Exact source archive](https://codeload.github.com/fishjar/kiss-translator/zip/3d03f21c50536d67a5ca457e0181f8cd4911b781).
- Archive SHA-256: `badcb3de031188230cb04e7b3ca14c6abf569be0119eb078a98f59c113cc0751`.
- Download timestamp: `2026-09-19T23:06:25.106196+08:00`.
- Extracted files: 507; each file byte-compared successfully with its ZIP entry.
- `git ls-remote https://github.com/fishjar/kiss-translator.git refs/heads/dev` returned this exact commit at acquisition time.
- GitHub comparison API was rate limited; the successful read-only Git query supplied branch verification instead.

## Local layout

Paths relative to the SmartTube-AI root:

```text
research-sources/kiss-translator/
  snapshot.json
  3d03f21c50536d67a5ca457e0181f8cd4911b781.zip
  3d03f21c50536d67a5ca457e0181f8cd4911b781/
    LICENSE
    README.md
    src/
    testdata/
```

Source files and LICENSE remain unchanged. Archives and extracted files are ignored by Git; this provenance note belongs in project documentation. No Gradle settings or submodule declarations were changed.

## Reading entry points

These paths exist in the snapshot; behavior remains to be reviewed:

| Investigation | Snapshot-relative path |
| --- | --- |
| Subtitle settings | `src/views/Options/Subtitle.js`, `src/hooks/Subtitle.js` |
| Caption retrieval and track identity | `src/subtitle/YouTubeCaptionProvider.js`, `src/subtitle/youtubeCaptionTracks.js` |
| Scheduling and lifecycle | `src/subtitle/BilingualSubtitleManager.js`, `src/subtitle/subtitle.js` |
| Response alignment | `src/libs/subtitleIndexAlign.js`, `src/apis/trans.subtitle.test.js` |
| Caption fixtures | `testdata/subtitle-samples/` |

## Outstanding research

Verify independence from webpage rules and inspect batching, prefetch, caching and cancellation using pinned line-level evidence. The planned independent SmartTube implementation preserves original timestamps; AI resegmentation and copied prompts are outside the first-version scope. Download success is not feature or device acceptance.

## Follow-up

Static investigation completed; see [integration research](smarttube-subtitle-translation.md). Acquisition evidence above remains unchanged. Runtime and device acceptance remain outstanding.
