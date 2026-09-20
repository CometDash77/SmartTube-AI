# 第二轮电视验收失败 —— 调试报告（供审查）

> **已独立复核，本文保留为历史报告。** 最新裁决见 [复核报告](subtitle-round2-independent-review-2026-09-20.md)，执行见 [第三轮实施计划](../subtitle-round3-implementation-plan.md)。本报告的失败计数 29、N1 守卫等价及重装清数据定因不成立；最新累计日志实际 12 次失败。用户后续明确确认未输入过 Key，该事实来自用户，不能仅由配置快照推出。

日期：2026-09-20（Asia/Hong_Kong）· 设备：TCL / Android 11 / SDK 30（`tc8000_ay30a2`）
被测候选：`32.53-nightly-18`（versionCode 2443，tip `666f5177`；上一轮验收候选为 `nightly-16` / `2f64f8a8`）
报告性质：**调试报告（diagnosis）**，本轮**未修改任何代码**；修复清单交审查者决定后再实施。

---

## 1. 结论摘要

1. **验收结论：不通过。** 用户报告的 5 条现象全部在日志中有对应证据；导出失败的原因**不是 AI 开关**，也不是写文件失败。
2. **导出的直接原因（已由证据证实）**：导出发生时会话内**没有任何"已绑定"的字幕来源**（`sourceBound=false`、`sourceType=unknown`），并且**整个会话从未发起过时间轴抓取**（`snapshotStatus=NOT_REQUESTED`，事件里 0 条 `TIMELINE_REQUESTED`，事件环未满）。没有可导出的原文，导出按设计拒绝，且**没有生成空文件**。
3. **真正的产品缺陷**：这个前提（必须先选中一条**文本**字幕轨）在应用里**没有任何提示**；失败提示反而说"请等待数秒后重试"，把用户引向错误方向。
4. **第二个产品缺陷（回答"Key 填在哪"）**：唯一的 Key 入口标签是 **"AI 翻译设置"**，弹出的输入框 **hint 也是"AI 翻译设置"**，全程没有出现"API Key"字样；保存成功后**没有成功提示**；菜单也**不显示 Key 是否已配置**（`ai_subtitle_key_configured`/`ai_subtitle_key_missing` 两条字符串在代码里引用次数为 **0**）。
5. **第三个缺陷（观察/诊断能力）**：现有诊断白名单**没有**"是否选中字幕轨 / 来源绑定状态 / 抓取为何被跳过"这三类字段，因此**无法只凭日志区分**"用户没选轨""轨存在但来源无法绑定""请求被内部守卫拦下"。这是本轮必须一起修的元问题，否则下一轮还会重复同样的往返。
6. **放大因素**：每次 CI 产出的候选都是**不同的 debug 签名**（本机实测 `nightly-15` 证书 `c26c1da0…`、`nightly-16` 证书 `57209913…`），跨签名无法覆盖安装 → 用户每轮都必须卸载重装 → 应用数据被清空（日志中 `keyConfigured=false`、`aiEnabled=false`、`targetLanguage` 从默认 `en` 被重新改为 `zh-Hans`、显示模式全为默认），**字幕轨/CC 偏好也被重置**，直接放大第 3 条的现象。
7. **状态行误导**：AI 开关关闭时，状态行显示的是 **"翻译中"**（`aiSubtitleStatusResId` 的 `default` 分支把 `AI_OFF` 也映射到 translating），这正是用户"看不懂这个区域在干什么"的直接来源之一。

---

## 2. 证据：四份诊断报告逐字段对照

四份原始文件均已按字节入库到 `docs/plans/evidence/device-logs/2026-09-20/`（SHA-256 与用户附件一致）：

| 文件 | SHA-256 | 版本 | 说明 |
| --- | --- | --- | --- |
| `SmartTube-diagnostics-20260920-113003.txt` | `5ace2bbc…` | nightly-9 | 上一轮**成功**导出原文的那次（历史证据） |
| `SmartTube-diagnostics-20260920-124740.txt` | `a82d1d8f…` | nightly-18 | 本轮第一次导出失败（AI 关闭） |
| `SmartTube-diagnostics-20260920-124854.txt` | `95b5e82e…` | nightly-18 | 本轮 AI 关闭→打开全过程（同一会话） |
| `SmartTube-diagnostics-20260920-124908.txt` | `b5758ee9…` | nightly-18 | 本轮最后一次（含 AI 开→关） |

### 2.1 关键字段对照

| 字段 | nightly-9（成功） | nightly-18（失败）×3 | 含义 |
| --- | --- | --- | --- |
| `sourceBound` | **true** | **false / false / false** | 导出时刻**没有已绑定的字幕来源** |
| `sourceType` / `sourceMime` | `asr` / `text/vtt` | `unknown` / `unknown` | 同上 |
| `sourceVssId` / `sourceLanguageCode` | `a.en` / `en` | `unknown` / `unknown` | 同上 |
| `snapshotStatus` | `OK` | `NOT_REQUESTED` ×3 | 从未发起抓取，不是抓取失败 |
| `timelineFrames` / `timelineItems` | 1040 / 520 | 0 / 0 | 无时间轴可导出 |
| 事件中的 `TIMELINE_REQUESTED` | 3 条（`+282517`…`+282521`） | **0 条** | 关键差异 |
| 事件中的 `SNAPSHOT_OK` | 3 条 | 0 条 | 同上 |
| `EXPORT_SUBTITLES_FAILED_NO_TIMELINE` | 0 | 3 / 13 / 13 条 | 用户反复重试 |
| `EXPORT_DIAGNOSTICS_OK` | 1（本轮报告来源） | 0 / 2 / 2 条 | **写文件能力正常** |
| `storageFreeBytes` | ≈20.0 GB | ≈19.8 GB | 空间充足 |
| `keyConfigured` | false | **false**（全程） | 用户**从未成功保存过 Key** |
| `aiEnabled` | false（导出时） | false→true→false（有切换记录） | AI 开关被正常操作 |
| `targetLanguage` | en | en → **zh-Hans** | 用户确实进入了 AI 区并改了语言 |
| `displayMode` | ORIGINAL_ONLY | ORIGINAL_ONLY | 默认值（数据被清空） |

### 2.2 会话时间线重建（nightly-18，基准 = 应用启动）

```
+99346ms  SOURCE_CHANGED                                   ← 用户换了视频/媒体源
+148594ms EXPORT_SUBTITLES_FAILED_NO_TIMELINE             ← 第 1 次导出（AI 关闭）
+174594ms EXPORT_SUBTITLES_FAILED_NO_TIMELINE
+183194ms EXPORT_SUBTITLES_FAILED_NO_TIMELINE
+185278ms EXPORT_DIAGNOSTICS_OK                           ← 诊断导出成功（证明写入正常）
+187994ms … +246928ms EXPORT_SUBTITLES_FAILED_NO_TIMELINE （共 5 次）
+249153ms AI_ENABLED                                      ← 用户打开 AI 开关
+251469ms … +258078ms EXPORT_SUBTITLES_FAILED_NO_TIMELINE （再 3 次，结果完全相同）
+259610ms EXPORT_DIAGNOSTICS_OK
+269658ms AI_DISABLED
+272028ms EXPORT_SUBTITLES_FAILED_NO_TIMELINE
```

推断：AI 打开**前后**导出结果是同一个失败码，且 `AI_ENABLED` 之后**仍无 `TIMELINE_REQUESTED`** → 失败与 AI 开关无关，与"没有可用的字幕来源"一致。

---

## 3. 用户 5 条现象 → 定因

| # | 用户描述 | 判定 | 证据 |
| --- | --- | --- | --- |
| 1 | 不开 AI、开了 AI 都导不出字幕 | **属实**；同一原因（无已绑定字幕来源），与 AI 开关无关 | `sourceBound=false`＋0 条 `TIMELINE_REQUESTED`；AI 前后同一失败码 |
| 2 | 没看到可以填 API Key 的地方；"AI 翻译设置"进去就是纯粹的输入 | **属实，是缺陷** | 入口字符串 = `ai_subtitle_settings`＝"AI 翻译设置"；`SimpleEditDialog.showPassword` 的 title 与 hint 都用这一条字符串，**没有任何一处出现 "Key"**；`ai_subtitle_key_configured`/`ai_subtitle_key_missing` 在 main 代码引用次数 = 0 |
| 3 | 能看到 AI 区，但因为填不了 Key、也改不了设置，所以测不了 | **属实**：Key 入口不可识别 + 保存后无反馈 + 状态行误导 | 同上；保存成功路径只在不持久时才 toast（`ai_subtitle_key_session_only`），成功时**无提示** |
| 4 | 导出字幕失败 | **属实**，原因见第 1 条与第 4 节 | `NO_TIMELINE` ×29 |
| 5 | 找不到配置 Key 的地方 | 同第 2 条 | 同第 2 条 |

用户看到的中文界面（`values-zh`）：开关"AI 翻译（本视频）"、目标语言、显示模式、服务地址、模型、翻译风格、"AI 翻译设置"、"测试连接"、状态行 → **状态行显示"翻译中"**（AI 其实是关的）、"导出字幕"/"导出诊断日志"。**整个区域没有一处提到 "Key"。**

---

## 4. 根因分析

### 4.1 已由证据证实

- **导出失败 = 无可导出的原文**：无绑定来源 → 协调器/请求被守卫拦下 → 无时间轴 → 导出按设计拒绝（不产生空文件）。
- **不是写文件失败**：同一会话 `EXPORT_DIAGNOSTICS_OK` 两次、空间约 19.8 GB。
- **不是 AI 开关问题**：开关前后结果一致。
- **Key 从未保存成功**：`keyConfigured=false` 全程。
- **跨签名重装导致数据被清空**：`aiEnabled=false`、`displayMode=ORIGINAL_ONLY`、`targetLanguage` 从默认被改动、以及本地实测两次候选证书不同。

### 4.2 现有证据**无法**区分（必须在下一轮补字段才能定论）

- **H-A 没有选中任何字幕轨**（CC 关闭 / 该视频无轨）：最可能，且与"数据被清空后 CC 偏好重置"一致。
- **H-B 选了轨但来源无法绑定**（`SubtitleSourceBinder.Status = SOURCE_UNKNOWN / SOURCE_AMBIGUOUS`，例如自动翻译轨或清单与来源列表不匹配）：屏幕上可能**有**字幕但 `sourceBound=false`，症状完全相同。
- **H-C 请求被内部守卫拦下**（`mAiSubtitleBinder == null`（无 `AiSubtitleHost`/字幕视图未就绪）、控制器源身份为 null、`Format` 或 payload factory 为 null）：这些提前返回**不写事件也不写状态**，因此日志无法排除。代码复核：与旧实现的守卫等价（唯一新增的 `sourceKey == null` 与 `source == null` 同源同真），因此**未发现"N1 改动使抓取变少"的证据**。
- **H-D 完全没有走播放器界面**（`getSubtitleDisplay()==null`）：同样会表现成 0 条请求。

> 因此本报告的准确表述是："本轮失败**不是** N1 请求身份改动的证据性回归；直接原因是当时没有可绑定的字幕来源；而'为什么没有'这件事当前的诊断无法回答。"

### 4.3 上一轮哪些假设被证据推翻

| 上一轮的假设 | 证据 | 修正 |
| --- | --- | --- |
| "菜单已经自解释，用户能找到 Key 入口" | 用户 3 条抱怨 + 字符串引用计数 | 推翻：入口标签不含 Key，且无状态显示 |
| "无 Key/无 AI 也能导出原文（已设备验收通过）" | nightly-9 那次之所以成功，是因为当时**已选中 ASR 轨** | 表述不完整：必须**先选中一条文本字幕轨**；应用需自己说清这一点 |
| "失败提示已经可操作" | 提示是"请等待数秒后重试" | 推翻：无轨时等待无用 |
| "AI 关闭时状态行不误导" | `AI_OFF` 走 `default` → "翻译中" | 推翻 |
| "唯一需要的外部条件是签名 Secrets" | 追加：跨签名重装清空数据，破坏验收可重复性 | 扩展 |

---

## 5. 诊断能力缺口（本轮最重要的产出）

当前白名单`SubtitleDiagnosticReport.WHITELIST`**缺**以下可安全打印的信息（全部为枚举/布尔/计数，不含 URL、正文、Key）：

| 建议新增字段 | 取值 | 用途 |
| --- | --- | --- |
| `subtitlesSelected` | true/false | 是否选中了字幕轨（`AiSubtitleHost.getSelectedSubtitleFormat() != null`） |
| `sourceStatus` | `BOUND`/`NOT_SELECTED`/`UNBOUND`/`SOURCE_UNKNOWN`/`SOURCE_AMBIGUOUS` | 区分"H-A 没轨"与"H-B 绑不上"（`ExoPlayerController.getSubtitleSourceBinder().getStatus()` 已可达） |
| `timelineRequests` / `timelineInstalls` | 计数 | 抓取是否发起/成功安装 |
| `timelineSkips` | 计数（或每个原因一条事件码） | 回答"H-C 守卫拦下" |

建议新增事件码（`SubtitleExportEventLog` 只保留 `[A-Z0-9_]`，天然安全）：
`SUBTITLES_ON`、`SUBTITLES_OFF`、`TIMELINE_SKIP_NO_BINDER`、`TIMELINE_SKIP_NO_SOURCE`、`TIMELINE_SKIP_NO_FORMAT`、`TIMELINE_SKIP_NO_FACTORY`、`TIMELINE_SKIP_NO_IDENTITY`、`TIMELINE_SKIP_ALREADY_HAVE`、`TIMELINE_SKIP_IN_FLIGHT`、`TIMELINE_REUSED`。

实现要点：把 `SubtitleTimelineCoordinator.request()` 的 `boolean` 改为返回一个结果枚举（`STARTED` / `REUSED` / `SKIPPED_*`），`PlaybackPresenter` 据此写事件与计数；`AiSubtitleHost` 增加 `getSubtitleBindingStatus()`。

---

## 6. 修复清单（**未实施**，供审查后决定）

优先级按"下一轮验收能否一次成功"排序。

### P0-1 导出失败必须给出可操作原因
- 位置：`PlayerUIController.aiSubtitleExportReason()`（`CODE_NO_TIMELINE` 分支）、`SubtitleExportSnapshot`（新增 `sourceStatus`/`subtitlesSelected`）、`PlaybackPresenter.buildAiExportSnapshot()`。
- 行为：无绑定来源 → "请先选择一条文本字幕轨（长按字幕键 → 选一条字幕）再导出原文"；来源不可识别 → "该字幕轨来源无法识别，请换一条轨或导出诊断日志"；已绑定但时间轴未就绪 → 保留现有"等待/重试"。
- 自动测试：把"快照状态 → 消息资源 id"提炼为纯函数并加单元测试（不依赖设备）。

### P0-2 诊断补齐上文第 5 节字段与事件码
- 这是**下一轮不再往返**的前提；同时保留"事件环 40 条"上限与隐私白名单。

### P0-3 Key 入口自解释 + 状态显示 + 保存反馈
- **需在第三轮明确区分的一种可能**：用户的"无法填写 API Key"可能是**标签/发现性**问题（他确实进了输入框、
  但不知道那是 Key），也可能是**输入本身不可用**（软键盘不弹出 / 遥控器焦点进不了输入框）。
  证据倾向前者：`SimpleEditDialog` 是本项目账号设置、通用设置、重命名分组等处共用的输入方式，
  且用户描述"进去就是纯粹的输入"说明输入框是可见的。第三轮请**显式记录**"能否输入一个字符"这一条。
  （另注：本轮修复清单实施后生成的候选中，Key 入口将直接标注 "API Key（DeepSeek）：未配置/已配置"，
  因此"标签"与"是否能输入"这两个可能在下一次测试中天然分离。）
- 新字符串（三语言）：入口改为 "API Key（DeepSeek）：未配置 / 已配置，点击输入或替换"；输入框 hint 改为 "在此输入 API Key，仅保存在本机"；保存成功 toast "API Key 已保存"。
- 复用当前**未被引用**的 `ai_subtitle_key_configured`/`ai_subtitle_key_missing`；入口固定放在 AI 区靠前位置（建议紧跟开关）。
- 保持既有安全契约：不回填、不打印、不进入诊断/备份；API 17–22 仍为会话级并已有提示。

### P1-1 状态行不再把 AI 关闭显示成"翻译中"
- 新增 `ai_subtitle_status_off`（"AI 翻译未开启（本视频）"），`aiSubtitleStatusResId` 显式映射 `AI_OFF`；或在关闭时隐藏状态行。

### P1-2 AI 区给出最小使用顺序 + 无轨时菜单内即提示
- 区首一行说明："① 选一条字幕轨 ② 输入 API Key ③ 打开翻译开关"。
- 把 `subtitlesSelected` 接入 `SubtitleAiMenuState.of(...)`，使 **AI 关闭时**也能显示"尚未选择字幕轨"，而不是靠导出失败后才知道。

### P1-3 稳定签名（N8）——验收可重复性的前提
- 每轮 debug 签名不同导致必须卸载重装并清空数据；需要 4 个 Secrets 产出项目签名候选后再开始下一轮设备验收（否则请至少在同一次安装内完成全部验收项）。

### P2 其它
- 导出按钮在"无绑定字幕轨"时可先给即时提示（本轮用户点了 29 次失败）。
- 拆分 `ai_subtitle_export_no_timeline` 文案为三条（无轨 / 绑不上 / 未就绪）。

---

## 7. 下一轮设备验收的最短路径（建议写进验收脚本）

1. **先选字幕**：播放视频 → 长按字幕键 → 选择一条**文本**字幕（英文/ASR 均可）→ 确认屏幕上出现字幕。
2. **导出原文**（不开 AI、不填 Key）→ 应得到 `original.srt` + `untranslated.srt` + `translation-status.txt` + `README.txt`。
3. 导出诊断日志 → 新报告里应能看到 `subtitlesSelected=true`、`sourceStatus=BOUND`、`timelineRequests≥1`。
4. **填 Key**：AI 区第一项 "API Key（DeepSeek）：未配置" → 输入 → 应看到"已保存"、菜单变为"已配置"。
5. 测试连接 → 三模式/seek/换轨 → 译文形态导出。
6. 若第 2 步仍失败：把诊断日志发回——新字段会直接指出是"没选轨""绑不上"还是"守卫拦下"。

---

## 8. 给审查者的开放问题（请决定后再实施）

1. P0-1 的"无轨提示"是否足够，还是需要**改动导出契约**（例如允许在没有绑定来源时，从当前屏幕上已显示的 cue 生成原文）？后者是新功能，会改变"导出只使用已取得的时间轴"这一既有契约。
2. 是否接受 P0-3 调整 AI 区**条目顺序/标签**（现有 7 项无分组）？是否应把 AI 区提升到主设置界面以解决"找不到"的问题？
3. P1-3：是否先要求签名 Secrets 到位再安排下一轮设备验收（推荐），还是接受"每次重装、一次装完验完"的方式继续？
4. 是否需要在本轮修复里一并处理"字幕轨选择事件也应触发时间轴准备"（当前依赖 ExoPlayer 的 `onTracksChanged` 回调；`selectFormat()` 只发 `onTrackSelected`）？本轮日志无法证明这是触发缺口，但它是 H-C 的一个具体子路径。

---

## 9. 本轮边界与隐私

- 本轮**未修改应用代码**、未提交修复、未改动子模块；只新增本报告、入库三份新诊断报告、更新计划与开发记录，并把工作流的 `paths-ignore` 扩展到 `docs/**`（证据/文档提交不再产生同代码候选）。因此本次提交会触发**一次**同代码构建（`nightly-19`），它不用于验收。
- 报告与日志**不含** Key、Authorization、Cookie、账号、签名 URL、HTTP 正文、字幕正文、logcat 或偏好设置；诊断文件按白名单生成，可直接回传。
- 唯一新增的第三方内容是**上一轮成功的字幕 ZIP** 未重复入库（`567409e6…` 已在仓库中）。
- 工作流配置验证：本次提交（工作流 + 文档 + 证据）触发的同代码构建 run
  [35490525057](https://github.com/CometDash77/SmartTube-AI/actions/runs/35490525057) 结论 **success**，
  并发布 `stbeta-32.53-nightly-19-…`（同代码，不作为验收版本）；随后一次**纯文档**提交用于验证
  `paths-ignore: '**/*.md', 'docs/**'` 生效（预期不产生新 run）。
