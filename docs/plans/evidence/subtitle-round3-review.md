# 第三轮字幕修复（R1–R4）Jev 语义审核与人工裁决

日期：2026-09-20，Asia/Hong_Kong。被审源码：`production` / `33c674b2`（R1–R4 实现提交；测试夹具修正见 `fdd3ea23`）。
范围：本轮改动的语义判断（readiness 与选轨的区分、先 resolve 再读 status、固定请求结果与计数、点击时快照导出原因、AI_OFF 映射、Key 保存反馈、字幕可见性事件）。不是全库审核，也不证明完成度。

## 1. 实际调用

- 1 次 live `audit`，8 条 claim/evidence；模型 `typesafe/jev-1.13-20260917`。
- usage：6,875 input / 371 output tokens，脚本上报 cost 0.00028875；耗时约 1,080 ms；**无重试、无 provider 回退**。
- 脚本上报的规范化输入 state sha256 `f88956671c40a68dcfe6167569dda225cb4aabb0612669becada3eaab1fdf526`；落盘输入文件 sha256 `fbee1786f816fee1fa96d826281072128039657b28cd56c3026bc552487738fd`（两者口径不同，均原样记录）。
- 输入 17,917 字节（< 20,000 上限），每个 check 含 `id/source/claim/evidence`；证据只含本仓库源码片段，不含设备日志、字幕正文、Key 或配置。
- 原始输入与结果：[input](subtitle-round3-review-jev-input.json)、[result](subtitle-round3-review-jev-result.json)。

阈值口径（本地策略，非正确性保证）：choice 需 confidence ≥ .70、胜出概率 ≥ .85、与次优 margin ≥ .20，否则由 Astra 回源裁决。

## 2. 逐条结果与人工裁决

| ID | Jev choice / probability / confidence | 处置 | Astra 裁决与依据 |
| --- | --- | --- | --- |
| readiness-not-notselected | supports / .84 / .77（未达 .85） | 人工复核 | **supports**。`reasonFor()` 在检查 `sourceStatus` 之前先返回 `NOT_READY`；`SubtitleAiMenuState.of()` 在 `!sourceBound → NO_SOURCE` 之前先返回 `NOT_READY`。反例检查：main 中只有两处"选轨"文案，分别只由 `NOT_SELECTED` 原因与 `NO_SOURCE` 状态产生（`PlayerUIController.java:629,650`）；本机 `aMissingPlayerIsExplainedAsNotReadyNotAsAnUnselectedTrack`、`anUnreadyPlayerIsReportedBeforeAMissingSource` 通过。 |
| status-belongs-to-selection | supports / .96 / .95 | 接受 | 快照先调用 host 的 `getSelectedSubtitleSource()`（内部 resolve 并写入 status），随后才读 `getSubtitleSourceStatus()`。 |
| skips-exclude-dedup | supports / .97 / .97 | 接受 | `IN_FLIGHT/REUSED/ALREADY_READY` 分支直接 return；只有 default 分支与 readiness 拒绝走 `recordAiTimelineSkip`。 |
| installs-only-current-accepted | supports / .96 / .95 | 接受 | `timelineInstalls` 只在 `accepted && installed` 时自增；`accepted` 由 `scheduler.settle(request)` 与 sourceKey 比对共同决定。 |
| reason-from-click-time-snapshot | supports / .55 / .33（未达阈值） | 人工复核 | **supports（补充证据后）**。给定片段只覆盖"原因来自 snapshot/结果对象"，未覆盖"快照在点击线程先取"。回源：`SubtitleExportController.start()` 在调用线程取 `snapshot = mSnapshotProvider.snapshot()`（`SubtitleExportController.java:265`）后才 `mExecutor.execute`，`build()` 只用该局部变量；UI 只读 `ExportResult`。本机 `theFailureReasonBelongsToTheSnapshotTakenAtClickTime`（点击 A 后切到 B）通过。 |
| ai-off-has-own-label | supports / .95 / .93 | 接受 | `AI_OFF` 有独立 case 与资源；default 只对未知枚举兜底，`TRANSLATING` 仍有显式 case。 |
| key-save-feedback-honest | insufficient / .68 | 人工复核 | **supports**。给定片段缺少提交/取消路径。回源：`SimpleEditDialog.java:112` 的 `boolean dismiss = onChange.onChange(newValue)` 决定是否关闭，保存失败回调返回 `false` 因此对话框保留；负按钮只调用 `configDialog.dismiss()`，不触碰 store；持久化提示由 `isKeyPersistent()` 二选一。 |
| visibility-events-on-change-only | supports / .84 / .76（未达 .85） | 人工复核 | **supports**。`trackAiSubtitleVisibility()` 在 `mAiSubtitlesVisible` 未变化时提前返回；全仓库只有 1 个调用点（`PlaybackPresenter.java:1139`，`onTrackChanged`），tick 与菜单绘制不调用它；换源与引擎释放把标记重置为未知。 |

合计：Jev 直接接受 4 条；4 条由人工回源裁决，全部结论为 supports，未出现反例。

## 3. 边界与不可读作完成的部分

- Jev 只对**给定片段与陈述的关系**做有界判断；它不证明编译、测试、签名、设备验收或任务完成，也不授权发布。
- 本审核基于 `33c674b2` 的源码；`fdd3ea23` 只修改测试夹具期望（`SubtitleTimelineCoordinatorTest` 一条断言），不改变被审语义，因此本轮结论仍适用于最终候选代码，但**不适用于**后续任何新的语义改动。
- 未向 Jev 发送设备日志、字幕正文、凭据、签名配置或工作流 Secrets；未做第二次调用以补齐输出（结果已即时落盘）。
- 仍未验证：CI 必需门禁在最终 SHA 的结果、APK 签名/资产哈希、真实设备输入与导出、真实付费调用、API 17–22 分支。
