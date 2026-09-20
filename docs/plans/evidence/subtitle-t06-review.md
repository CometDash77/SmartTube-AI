# T06/T10 Jev audit

Date: 2026-09-20, Asia/Hong_Kong. Scope: cross-state semantics of the prefetch chain (single call gate,
cancellation, retry budget, duplicate dispatch, snapshot cancellation safety).

## Actual call

```powershell
python -B .agents/skills/smarttube-jev-triage/scripts/jev.py audit tmp/jev-subtitle-t06-input.json --live
```

- Mode `audit`, status `judged`, exactly 1 call, no retry, no fallback.
- Model `typesafe/jev-1.13-20260917`; 2,331 input / 221 output tokens; cost 0.000097902.
- `input_sha256` `828fa87cb76c...`. Input [subtitle-t06-jev-input.json](subtitle-t06-jev-input.json),
  raw result [subtitle-t06-jev-result.json](subtitle-t06-jev-result.json). 5 checks.

Thresholds: confidence >= 0.70, winner >= 0.85, margin >= 0.20. **All 5 rows passed** - the first batch
in this session where no manual adjudication was needed.

| id | Jev answer | probability / confidence | Margin |
| --- | --- | --- | --- |
| `single-call-gate` | supports | 0.96 / 0.93 | 0.95 |
| `cancel-abandoned` | supports | 0.97 / 0.95 | 0.97 |
| `retry-budget` | supports | 0.98 / 0.96 | 0.98 |
| `no-duplicate-dispatch` | supports | 0.85 / 0.77 | 0.85 |
| `snapshot-cancel-safety` | supports | 0.96 / 0.93 | 0.94 |

`not_checked`: evidence authenticity/freshness, code correctness, overall task completion. A passing
row is a bounded judgement on the quoted evidence only; it is not a substitute for the unit tests that
carry the same claims, and it does not certify T06 or the feature as complete.

## Notes for the remaining work

- The weakest row (`no-duplicate-dispatch`, winner 0.85) is exactly at the threshold; its two unit
  tests remain the authoritative evidence.
- No claim here covers device behaviour, the Android keystore, real DeepSeek calls or the UI, so those
  remain unverified.
